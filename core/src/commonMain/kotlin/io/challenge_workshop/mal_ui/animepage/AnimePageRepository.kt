package io.challenge_workshop.mal_ui.animepage

import io.challenge_workshop.mal_ui.animelist.AnimeListEntry
import io.challenge_workshop.mal_ui.animelist.AnimeListRepository
import io.challenge_workshop.mal_ui.session.MalSessionRepository
import io.challenge_workshop.mal_ui.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * The open Anime Pages, for as long as there is a Session — the same rule, kept the same way, as
 * [AnimeListRepository]. See `docs/adr/0006-anime-page-history-in-screen-state.md`.
 *
 * **The history closes when the Session ends, and only then.** A filter change, a Sort Order change
 * and a Reload are the Anime List's business and never reach this module, so none of them can close
 * a page. A page still fetching when the Session ends is cancelled and discarded with it.
 *
 * **Opening an anime draws it at once** from the list row and then fetches `GET /anime/{id}`. When
 * the fetch lands, MAL's fresh `my_list_status` is also written into the Anime List's loaded entry,
 * because the row may have been stale and the two should not disagree.
 *
 * The operations do not suspend: each acts on the state synchronously and launches the fetch into
 * the current Session's scope. Outside a Session there is no scope and they do nothing.
 *
 * @param scope `Dispatchers.Main.immediate` in the app, like [AnimeListRepository].
 */
class AnimePageRepository(
    private val session: MalSessionRepository,
    private val animeList: AnimeListRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(AnimePageHistory())

    /** The current Session's open pages, or none when there is no Session. */
    val state: StateFlow<AnimePageHistory> = _state.asStateFlow()

    private var sessionScope: CoroutineScope? = null

    /** The fetches in flight, by anime, so leaving a page or opening another can cancel them. */
    private val fetches = mutableMapOf<Long, Job>()

    init {
        scope.launch {
            session.state
                .map { it is SessionState.SignedIn }
                .distinctUntilChanged()
                .collect { signedIn -> if (signedIn) begin() else end() }
        }
    }

    private fun begin() {
        sessionScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    }

    private fun end() {
        sessionScope?.cancel()
        sessionScope = null
        fetches.clear()
        _state.value = AnimePageHistory()
    }

    /**
     * Opens [entry]'s Anime Page as a **new history**, replacing whatever was open. Drawn from the
     * row at once; the fetch fills it in.
     */
    fun open(entry: AnimeListEntry) {
        if (sessionScope == null) return
        cancelFetches()
        _state.value = AnimePageHistory(listOf(AnimePage.from(entry)))
        fetch(entry.animeId)
    }

    /** One page back; from the only page, closes. The fetch of the page left is abandoned. */
    fun back() {
        val history = _state.value
        val left = history.current ?: return
        fetches.remove(left.animeId)?.cancel()
        _state.value = AnimePageHistory(history.pages.dropLast(1))
    }

    /** Closes every open page. */
    fun close() {
        cancelFetches()
        _state.value = AnimePageHistory()
    }

    /** Asks again for the current page, if its fetch failed. */
    fun retry() {
        val page = _state.value.current ?: return
        if (page.load !is AnimePageLoad.Failed) return
        replace(page.animeId) { it.copy(load = AnimePageLoad.Loading) }
        fetch(page.animeId)
    }

    private fun cancelFetches() {
        fetches.values.forEach { it.cancel() }
        fetches.clear()
    }

    private fun replace(animeId: Long, transform: (AnimePage) -> AnimePage) {
        _state.update { history ->
            AnimePageHistory(history.pages.map { if (it.animeId == animeId) transform(it) else it })
        }
    }

    private fun fetch(animeId: Long) {
        val sessionScope = sessionScope ?: return
        fetches.remove(animeId)?.cancel()
        fetches[animeId] = sessionScope.launch {
            try {
                val details = session.animeClient().anime(animeId)
                // The list first, so it is never behind a page that already shows the newer status.
                details.listStatus?.let { animeList.applyListStatus(animeId, it) }
                replace(animeId) { it.loadedWith(details) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // `MalAnimeClient` has already put a transport failure into words. Anything else
                // that got here is a bug, and a retryable error beats one that ends the scope.
                replace(animeId) { it.copy(load = AnimePageLoad.Failed(e.message ?: e.toString())) }
            }
        }
    }
}

package io.challenge_workshop.mal_ui.animepage

import io.challenge_workshop.mal_ui.animelist.AnimeListEntry
import io.challenge_workshop.mal_ui.animelist.AnimeListRepository
import io.challenge_workshop.mal_ui.session.MalSessionRepository
import io.challenge_workshop.mal_ui.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

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
 * **An edit is worked out with MAL's automatic rules** ([applyAutomaticRules]) before it becomes the
 * target, so what they add is pending with the edit and goes back with it on failure.
 *
 * @param scope `Dispatchers.Main.immediate` in the app, like [AnimeListRepository].
 */
class AnimePageRepository(
    private val session: MalSessionRepository,
    private val animeList: AnimeListRepository,
    private val scope: CoroutineScope,
    /** The device's local date, for the automatic rules. A parameter so tests do not depend on the clock. */
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
) {
    private val _state = MutableStateFlow(AnimePageHistory())

    /** The current Session's open pages, or none when there is no Session. */
    val state: StateFlow<AnimePageHistory> = _state.asStateFlow()

    private var sessionScope: CoroutineScope? = null

    /** The fetches in flight, by anime, so leaving a page or opening another can cancel them. */
    private val fetches = mutableMapOf<Long, Job>()

    /**
     * Each anime's unfinished save, whether or not its page is still open — **the one copy the save
     * loop reads**, with the page's [AnimePage.save] a projection of it. That is what lets leaving a
     * page mid-save not lose the save: the loop never asks the history whether anyone is looking.
     */
    private val saves = mutableMapOf<Long, PageSave>()

    /** The anime with a save loop running: the "at most one PATCH in flight per anime" guard. */
    private val draining = mutableSetOf<Long>()

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
        saves.clear()
        draining.clear()
        _state.value = AnimePageHistory()
    }

    /**
     * Opens [entry]'s Anime Page as a **new history**, replacing whatever was open. Drawn from the
     * row at once; the fetch fills it in.
     */
    fun open(entry: AnimeListEntry) {
        if (sessionScope == null) return
        cancelFetches()
        _state.value = AnimePageHistory(listOf(AnimePage.from(entry).copy(save = saves[entry.animeId] ?: PageSave())))
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

    /**
     * Applies [edit] to the current page's List Entry and saves it, immediately if nothing is in
     * flight for that anime and as the next PATCH if something is. Does nothing until the page's
     * fetch has succeeded ([AnimePage.canEdit]), and nothing for an edit that changes nothing.
     */
    fun edit(edit: ListEdit) {
        val sessionScope = sessionScope ?: return
        val page = _state.value.current ?: return
        if (!page.canEdit) return
        val id = page.animeId
        val save = saves[id] ?: PageSave()
        val shown = page.shownListStatus!!
        val target = applyAutomaticRules(shown, edit.applyTo(shown, page.totalEpisodes), today(), page.totalEpisodes)
        if (target == shown) return
        saves[id] = save.copy(target = target, error = null)
        publish(id)
        // Undispatched, so the first tap is at MAL before the second can be made, whatever the
        // dispatcher: the loop below is what makes the later ones wait, and it must already be there.
        if (draining.add(id)) {
            sessionScope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    drain(id, page.listStatus!!)
                } finally {
                    // A loop cancelled by the Session ending must not release a later Session's guard.
                    if (sessionScope === this@AnimePageRepository.sessionScope) draining.remove(id)
                }
            }
        }
    }

    /**
     * Sends [id]'s target, and again for as long as it has moved on since the last send.
     *
     * Each send is only the fields that differ from the one before, so a later edit of another field
     * does not repeat an earlier one. MAL's answer becomes the confirmed value; a refusal ends the
     * loop and puts the page back on the last one.
     */
    private suspend fun drain(id: Long, confirmed: MyListStatus) {
        var baseline = confirmed
        while (true) {
            val target = saves[id]?.target ?: return
            val update = target.diffFrom(baseline)
            if (update.isEmpty) {
                saves.remove(id)
                publish(id)
                return
            }
            saves[id] = saves.getValue(id).copy(inFlight = target)
            publish(id)
            val answer = try {
                session.animeClient().updateListStatus(id, update)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saves.remove(id)
                publish(id, error = e.message ?: e.toString())
                return
            }
            baseline = target
            animeList.applyListStatus(id, answer)
            val now = saves.getValue(id)
            if (now.target == target) saves.remove(id) else saves[id] = now.copy(inFlight = null)
            publish(id, confirmed = answer)
        }
    }

    /** Puts [saves]' word for [animeId] on every open page of it, and MAL's [confirmed] answer if there is one. */
    private fun publish(animeId: Long, confirmed: MyListStatus? = null, error: String? = null) {
        val save = saves[animeId] ?: PageSave(error = error)
        replace(animeId) { it.copy(save = save, listStatus = confirmed ?: it.listStatus) }
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
                // A save under way is newer than this answer, which may predate it: keep the page's
                // confirmed value and the list's, and let the PATCH's own answer replace them.
                val saving = saves.containsKey(animeId)
                if (!saving) details.listStatus?.let { animeList.applyListStatus(animeId, it) }
                replace(animeId) {
                    it.loadedWith(details).let { loaded ->
                        if (saving) loaded.copy(listStatus = it.listStatus) else loaded
                    }
                }
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

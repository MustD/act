package io.challenge_workshop.mal_ui.animepage

import io.challenge_workshop.mal_ui.animelist.AnimeListEntry
import io.challenge_workshop.mal_ui.animelist.AnimeListRepository
import io.challenge_workshop.mal_ui.animelist.ListEntry
import io.challenge_workshop.mal_ui.animelist.WatchStatus
import io.challenge_workshop.mal_ui.session.MalSessionRepository
import io.challenge_workshop.mal_ui.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
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
 * **Saving is [ListEntrySaves]' business**, one instance per Session. This class gates the calls on
 * what a page can do, and projects the Saves' state onto the pages: their [AnimePage.save], and
 * every answer MAL gives written into the `listEntry` of each open page of that anime.
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

    private val _log = MutableStateFlow(PatchLog())

    /**
     * The last PATCH sent and whether any Save is pending, for the log bar. Unlike [state] it is not
     * cleared by [close]: a Save outlives its page. It ends with the Session.
     */
    val log: StateFlow<PatchLog> = _log.asStateFlow()

    private var sessionScope: CoroutineScope? = null

    /** The fetches in flight, by anime, so leaving a page or opening another can cancel them. */
    private val fetches = mutableMapOf<Long, Job>()

    /** The current Session's Saves, outliving any page; null outside a Session. */
    private var saves: ListEntrySaves? = null

    init {
        scope.launch {
            session.state
                .map { it is SessionState.SignedIn }
                .distinctUntilChanged()
                .collect { signedIn -> if (signedIn) begin() else end() }
        }
    }

    private fun begin() {
        val sessionScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        this.sessionScope = sessionScope
        val saves = ListEntrySaves(
            scope = sessionScope,
            send = { id, update -> session.animeClient().updateListEntry(id, update) },
            onConfirmed = ::confirmed,
            today = today,
        )
        this.saves = saves
        // Unconfined, so a Save's change is on the pages by the time the call that made it returns.
        sessionScope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            saves.state.collect { project(it) }
        }
    }

    private fun end() {
        sessionScope?.cancel()
        sessionScope = null
        saves = null
        fetches.clear()
        _state.value = AnimePageHistory()
        _log.value = PatchLog()
    }

    /** MAL's answer to a Save: the Anime List's row, and every open page of that anime. */
    private fun confirmed(animeId: Long, entry: ListEntry) {
        animeList.applyListEntry(animeId, entry)
        replace(animeId) { it.copy(listEntry = entry) }
    }

    /** Puts each Save on the open page of its anime, and the Patch Log on [log]. */
    private fun project(saves: ListEntrySavesState) {
        _log.value = saves.log
        _state.update { history ->
            AnimePageHistory(history.pages.map { it.copy(save = saves.of(it.animeId)) })
        }
    }

    private fun currentSaves(): ListEntrySavesState = saves?.state?.value ?: ListEntrySavesState()

    /**
     * Opens [entry]'s Anime Page as a **new history**, replacing whatever was open. Drawn from the
     * row at once; the fetch fills it in.
     */
    fun open(entry: AnimeListEntry) {
        if (sessionScope == null) return
        cancelFetches()
        val save = currentSaves().of(entry.animeId)
        _state.value = AnimePageHistory(listOf(AnimePage.from(entry).copy(save = save)))
        fetch(entry.animeId)
    }

    /**
     * Opens [related]'s Anime Page **on top of** the history, drawn from its cover and title at
     * once. The page it came from stays where it is, fetch and Save untouched, for [back].
     */
    fun open(related: RelatedAnime) {
        if (sessionScope == null) return
        val history = _state.value
        if (!history.isOpen) return
        _state.value = AnimePageHistory(
            history.pages + AnimePage.from(related).copy(save = currentSaves().of(related.animeId)),
        )
        fetch(related.animeId)
    }

    /**
     * One page back; from the only page, closes. The fetch of the page left is abandoned — unless
     * the same anime is also open further down (A → B → A), because a fetch fills every open page
     * of its anime and the one left below would otherwise be stuck loading.
     */
    fun back() {
        val history = _state.value
        val left = history.current ?: return
        val remaining = history.pages.dropLast(1)
        if (remaining.none { it.animeId == left.animeId }) fetches.remove(left.animeId)?.cancel()
        _state.value = AnimePageHistory(remaining)
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
     * Applies [change] to the current page's List Entry and saves it, immediately if nothing is in
     * flight for that anime and as the next PATCH if something is. Does nothing until the page's
     * fetch has succeeded ([AnimePage.canEdit]), and nothing for an edit that changes nothing.
     */
    fun edit(change: ListEdit) {
        val saves = saves ?: return
        val page = _state.value.current ?: return
        val confirmed = page.listEntry?.takeIf { page.canEdit } ?: return
        saves.edit(page.anime, confirmed, change)
    }

    /**
     * Adds the current page's anime to the user's list with [watchStatus]; see [ListEntrySaves.add].
     *
     * The page stays "not on your list" while it is pending, and **goes back to it with the error**
     * if MAL refuses, because [AnimePage.listEntry] only becomes non-null with MAL's answer. The
     * Anime List is not touched: its pager has no entry to update, and the new one appears on the
     * next Reload. Does nothing until the fetch has succeeded, or for an anime already on the list.
     */
    fun add(watchStatus: WatchStatus) {
        val saves = saves ?: return
        val page = _state.value.current ?: return
        if (page.load != AnimePageLoad.Loaded || page.listEntry != null) return
        saves.add(page.anime, watchStatus)
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
                // The list first, so it is never behind a page that already shows the newer List Entry.
                // A Save under way is shown over this answer, and its own answer replaces it — on the
                // Anime List too, so a fetch that predates the Save never puts a stale row there.
                val saving = currentSaves().isSaving(animeId)
                if (!saving) details.listEntry?.let { animeList.applyListEntry(animeId, it) }
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

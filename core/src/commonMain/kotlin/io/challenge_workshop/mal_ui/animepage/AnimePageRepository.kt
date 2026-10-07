package io.challenge_workshop.mal_ui.animepage

import io.challenge_workshop.mal_ui.animelist.AnimeListEntry
import io.challenge_workshop.mal_ui.animelist.AnimeListRepository
import io.challenge_workshop.mal_ui.animelist.ListEntry
import io.challenge_workshop.mal_ui.animelist.WatchStatus
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
import kotlin.time.TimeSource

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

    private val _log = MutableStateFlow(SaveLog())

    /**
     * The last PATCH sent and whether any save is pending, for the log bar. Unlike [state] it is not
     * cleared by [close]: a save outlives its page. It ends with the Session.
     */
    val log: StateFlow<SaveLog> = _log.asStateFlow()

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
        _log.value = SaveLog()
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

    /**
     * Opens [related]'s Anime Page **on top of** the history, drawn from its cover and title at
     * once. The page it came from stays where it is, fetch and saves untouched, for [back].
     */
    fun open(related: RelatedAnime) {
        if (sessionScope == null) return
        val history = _state.value
        if (!history.isOpen) return
        _state.value = AnimePageHistory(
            history.pages + AnimePage.from(related).copy(save = saves[related.animeId] ?: PageSave()),
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
        val shown = page.shownListEntry!!
        val target =
            applyAutomaticRules(shown, edit.applyTo(shown, page.anime.totalEpisodes), today(), page.anime.totalEpisodes)
        if (target == shown) return
        saves[id] = save.copy(target = target, error = null)
        publish(id)
        startSaving(id, page.anime.title, sessionScope, page.listEntry!!)
    }

    /**
     * Adds the current page's anime to the user's list with [watchStatus], worked out with what
     * myanimelist.net fills in for that Watch Status ([newListEntry]). It is the same PATCH as an edit, which
     * creates the entry.
     *
     * The page stays "not on your list" while it is pending, and **goes back to it with the error**
     * if MAL refuses, because [AnimePage.listEntry] only becomes non-null with MAL's answer. The
     * Anime List is not touched: its pager has no entry to update, and the new one appears on the
     * next Reload. Does nothing until the fetch has succeeded, for an anime already on the list, or
     * while an add is pending.
     */
    fun add(watchStatus: WatchStatus) {
        val sessionScope = sessionScope ?: return
        val page = _state.value.current ?: return
        if (page.load != AnimePageLoad.Loaded || page.listEntry != null) return
        val id = page.animeId
        if (saves.containsKey(id)) return
        saves[id] = PageSave(target = newListEntry(watchStatus, today(), page.anime.totalEpisodes))
        publish(id)
        startSaving(id, page.anime.title, sessionScope, NOT_ON_LIST)
    }

    /**
     * Starts [id]'s save loop unless one is running, against [confirmed] as the last value MAL held.
     * Undispatched, so the first tap is at MAL before the second can be made, whatever the
     * dispatcher: the loop is what makes the later ones wait, and it must already be there.
     */
    private fun startSaving(id: Long, title: String, sessionScope: CoroutineScope, confirmed: ListEntry) {
        if (draining.add(id)) {
            sessionScope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    drain(id, title, confirmed)
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
    private suspend fun drain(id: Long, title: String, confirmed: ListEntry) {
        var baseline = confirmed
        while (true) {
            val target = saves[id]?.target ?: return
            val update = target.diffFrom(baseline)
            if (update.isEmpty) {
                saves.remove(id)
                publish(id)
                _log.update { it.copy(pending = saves.isNotEmpty()) }
                return
            }
            saves[id] = saves.getValue(id).copy(inFlight = target)
            publish(id)
            val sentAt = TimeSource.Monotonic.markNow()
            logSave(title, update, SaveOutcome.Sent)
            val answer = try {
                session.animeClient().updateListEntry(id, update)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.message ?: e.toString()
                saves.remove(id)
                publish(id, error = message, errorFields = update)
                logSave(title, update, SaveOutcome.Refused(message))
                return
            }
            baseline = target
            animeList.applyListEntry(id, answer)
            val now = saves.getValue(id)
            if (now.target == target) saves.remove(id) else saves[id] = now.copy(inFlight = null)
            publish(id, confirmed = answer)
            logSave(title, update, SaveOutcome.Accepted(sentAt.elapsedNow().inWholeMilliseconds))
        }
    }

    /** Records [update] as the last PATCH, with what became of it; `pending` is read off [saves] each time. */
    private fun logSave(title: String, update: ListEntryUpdate, outcome: SaveOutcome) {
        _log.value = SaveLog(LoggedSave(title, update, outcome), pending = saves.isNotEmpty())
    }

    /** Puts [saves]' word for [animeId] on every open page of it, and MAL's [confirmed] answer if there is one. */
    private fun publish(
        animeId: Long,
        confirmed: ListEntry? = null,
        error: String? = null,
        errorFields: ListEntryUpdate = ListEntryUpdate(),
    ) {
        val save = saves[animeId] ?: PageSave(error = error, errorFields = errorFields)
        replace(animeId) { it.copy(save = save, listEntry = confirmed ?: it.listEntry) }
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
                // A save under way is newer than this answer, which may predate it: keep the page's
                // confirmed value and the list's, and let the PATCH's own answer replace them.
                val saving = saves.containsKey(animeId)
                if (!saving) details.listEntry?.let { animeList.applyListEntry(animeId, it) }
                replace(animeId) {
                    it.loadedWith(details).let { loaded ->
                        if (saving) loaded.copy(listEntry = it.listEntry) else loaded
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

/**
 * What MAL holds for an anime that is not on the list, as a baseline for the PATCH that creates the
 * entry: its Watch Status is one nobody can choose, so the chosen one always differs and is always sent.
 */
private val NOT_ON_LIST = ListEntry(
    watchStatus = WatchStatus.Unknown,
    score = 0,
    episodesWatched = 0,
    startDate = null,
    finishDate = null,
    updatedAt = null,
)

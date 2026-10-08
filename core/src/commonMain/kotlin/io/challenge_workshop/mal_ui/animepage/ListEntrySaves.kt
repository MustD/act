package io.challenge_workshop.mal_ui.animepage

import io.challenge_workshop.mal_ui.animelist.Anime
import io.challenge_workshop.mal_ui.animelist.ListEntry
import io.challenge_workshop.mal_ui.animelist.WatchStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource

/** Every anime's Save for one Session, and the log of what was sent. */
data class ListEntrySavesState(
    /** By anime: a Save under way, or the error of the last one that was refused. Settled ones are absent. */
    private val byAnime: Map<Long, Save> = emptyMap(),
    val log: PatchLog = PatchLog(),
) {
    /** [animeId]'s unsettled Save, or null when it has none. */
    operator fun get(animeId: Long): Save? = byAnime[animeId]

    /** [animeId]'s Save, an empty one when it has none — what a page of it is drawn with. */
    fun of(animeId: Long): Save = byAnime[animeId] ?: Save()

    /** Whether [animeId] has an edit not yet confirmed by MAL; a refusal's leftover error is not one. */
    fun isSaving(animeId: Long): Boolean = byAnime[animeId]?.target != null

    internal fun withSave(animeId: Long, save: Save?): ListEntrySavesState {
        val saves = if (save == null) byAnime - animeId else byAnime + (animeId to save)
        return copy(byAnime = saves)
    }

    internal val anySaving: Boolean get() = byAnime.values.any { it.target != null }
}

/**
 * The Saves of one Session: an edit worked out, sent as a PATCH, and its outcome — one module,
 * so the path from [ListEdit] to the wire is read in one place.
 *
 * **An edit is worked out in three steps, all private here.** [ListEdit.applyTo] says what the user
 * asked for; the automatic rules add what myanimelist.net would have filled in; [diffFrom] reduces
 * the result to the fields that differ from what MAL last held. The rules' `before` is the entry
 * **as shown** — a pending target included — so a rule fires on the transition the edit caused and
 * never on one an earlier, still pending edit already made.
 *
 * **At most one PATCH is in flight per anime.** Edits made meanwhile only move the target and go as
 * the next PATCH, so they can neither arrive out of order nor be sent one by one. Saves of different
 * anime do not wait for each other. A Save outlives any page: nothing here knows whether one is open.
 *
 * **Who wins, Save or fetch.** While a Save has a target its value is what a page shows. Every
 * accepted answer goes out through [onConfirmed]; a refusal sends the last one of that Save again,
 * so a stale fetch that landed mid-Save is not what the page falls back to, and keeps only the
 * error — until the next edit of that anime, or the end of the Session, so reopening still shows it.
 *
 * It never sees `SessionState`: the owner builds one per Session and drops it, cancelling [scope].
 *
 * @param send the PATCH. Throws when MAL refuses it or does not answer; the message is shown.
 * @param onConfirmed told every answer MAL gives, for an anime's pages and the Anime List.
 * @param today the device's local date, for the automatic rules; a parameter so tests need no clock.
 */
class ListEntrySaves(
    private val scope: CoroutineScope,
    private val send: suspend (animeId: Long, ListEntryUpdate) -> ListEntry,
    private val onConfirmed: (animeId: Long, ListEntry) -> Unit,
    private val today: () -> LocalDate,
) {
    private val _state = MutableStateFlow(ListEntrySavesState())
    val state: StateFlow<ListEntrySavesState> = _state.asStateFlow()

    /** The anime with a loop running: the "at most one PATCH in flight" guard. */
    private val draining = mutableSetOf<Long>()

    /** What MAL last answered during each unsettled Save, ahead of the page's own copy. */
    private val answers = mutableMapOf<Long, ListEntry>()

    /**
     * Applies [change] to [anime]'s List Entry and saves it, immediately if nothing is in flight for
     * that anime and as the next PATCH if something is. Does nothing for an edit that changes nothing.
     *
     * @param confirmed the page's List Entry, only the starting point: a Save's own target, or its
     * last answer, takes precedence.
     */
    fun edit(anime: Anime, confirmed: ListEntry, change: ListEdit) {
        val id = anime.animeId
        val current = _state.value[id]
        val shown = current?.target ?: answers[id] ?: confirmed
        val total = anime.totalEpisodes
        val target = applyAutomaticRules(shown, change.applyTo(shown, total), today(), total)
        if (target == shown) return
        set(id, Save(target = target, inFlight = current?.inFlight))
        start(id, anime.title, confirmed)
    }

    /**
     * Adds [anime] to the user's list with [watchStatus], worked out with what myanimelist.net fills
     * in for it. It is the same PATCH as an edit, which creates the entry. Ignored while an add for
     * that anime is pending. Kept apart from [edit]: the starting point is no entry at all.
     */
    fun add(anime: Anime, watchStatus: WatchStatus) {
        val id = anime.animeId
        if (_state.value.isSaving(id)) return
        set(id, Save(target = newListEntry(watchStatus, today(), anime.totalEpisodes)))
        start(id, anime.title, NOT_ON_LIST)
    }

    /**
     * Starts [id]'s loop unless one is running, against [confirmed] as the last value MAL held.
     * Undispatched, so the first tap is at MAL before the second can be made, whatever the
     * dispatcher: the loop is what makes the later ones wait, and it must already be there.
     */
    private fun start(id: Long, title: String, confirmed: ListEntry) {
        if (!draining.add(id)) return
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                drain(id, title, confirmed)
            } finally {
                draining.remove(id)
            }
        }
    }

    /**
     * Sends [id]'s target, and again for as long as it has moved on since the last send. Each send
     * is only the fields that differ from the one before, so a later edit of another field does not
     * repeat an earlier one. MAL's answer is not compared with the target — it can legitimately
     * differ, and would be chased for ever. A refusal ends the loop.
     */
    private suspend fun drain(id: Long, title: String, confirmed: ListEntry) {
        var baseline = confirmed
        while (true) {
            val target = _state.value[id]?.target ?: return
            val update = target.diffFrom(baseline)
            if (update.isEmpty) {
                answers.remove(id)
                set(id, null)
                return
            }
            set(id, Save(target = target, inFlight = target), LoggedPatch(title, update, PatchOutcome.Sent))
            val sentAt = TimeSource.Monotonic.markNow()
            val answer = try {
                send(id, update)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.message ?: e.toString()
                // Say again what MAL last held, in case a fetch that predates it landed meanwhile.
                answers.remove(id)?.let { onConfirmed(id, it) }
                set(
                    id,
                    Save(error = message, errorFields = update),
                    LoggedPatch(title, update, PatchOutcome.Refused(message)),
                )
                return
            }
            baseline = target
            answers[id] = answer
            // Before the Save moves on, so a page never shows neither the target nor the answer.
            onConfirmed(id, answer)
            val now = _state.value.of(id)
            val accepted = LoggedPatch(title, update, PatchOutcome.Accepted(sentAt.elapsedNow().inWholeMilliseconds))
            if (now.target == target) {
                answers.remove(id)
                set(id, null, accepted)
            } else {
                set(id, now.copy(inFlight = null), accepted)
            }
        }
    }

    /** Replaces [id]'s Save (none, when null) and, when there is one, records [logged] as the last PATCH. */
    private fun set(id: Long, save: Save?, logged: LoggedPatch? = null) {
        _state.update { state ->
            val next = state.withSave(id, save)
            next.copy(log = PatchLog(logged ?: state.log.last, pending = next.anySaving))
        }
    }
}

/**
 * The rules myanimelist.net applies to a List Entry that MAL's API does **not** — so every one is
 * the app's job and none can be applied twice (`docs/mal-api/list-status-probe.md`).
 *
 * [before] is the entry as it was shown and [requested] is what the user's edit made of it, so a
 * rule fires on a *transition* the edit caused and never on a state the entry was already in: a
 * user who clears the finish date of a Completed entry is not handed it back.
 *
 * Nothing applies while the total is unknown ([totalEpisodes] `<= 0`), and a date is only ever
 * **filled when empty**, never overwritten. Results are part of the returned target, so they are
 * shown as pending with the change that triggered them and go back with it on failure.
 *
 * - Progress from 0 to more on a Plan to Watch entry → Watching, start date [today].
 * - Progress reaching the total → Completed, finish date [today].
 * - Watch Status set to Completed → progress set to the total, finish date [today] — the same
 *   finish date adding it as Completed gets ([newListEntry]), so the two ways of arriving agree.
 */
private fun applyAutomaticRules(
    before: ListEntry,
    requested: ListEntry,
    today: LocalDate,
    totalEpisodes: Int,
): ListEntry {
    if (totalEpisodes <= 0) return requested
    var result = requested
    val todayText = today.toString()

    if (before.watchStatus == WatchStatus.PlanToWatch && requested.watchStatus == WatchStatus.PlanToWatch &&
        before.episodesWatched == 0 && requested.episodesWatched > 0
    ) {
        result = result.copy(watchStatus = WatchStatus.Watching, startDate = result.startDate ?: todayText)
    }
    if (requested.episodesWatched == totalEpisodes && before.episodesWatched != totalEpisodes) {
        result = result.copy(watchStatus = WatchStatus.Completed, finishDate = result.finishDate ?: todayText)
    }
    if (requested.watchStatus == WatchStatus.Completed && before.watchStatus != WatchStatus.Completed) {
        result = result.copy(episodesWatched = totalEpisodes, finishDate = result.finishDate ?: todayText)
    }
    return result
}

/**
 * The List Entry an anime gets when the user adds it by choosing [watchStatus], so the first PATCH
 * carries what myanimelist.net would have filled in (the spec's table):
 *
 * - Watching → start date [today].
 * - Completed → progress set to the total (when known) and finish date [today].
 * - Plan to Watch, On Hold, Dropped → nothing.
 */
private fun newListEntry(watchStatus: WatchStatus, today: LocalDate, totalEpisodes: Int): ListEntry {
    val entry =
        ListEntry(watchStatus, score = 0, episodesWatched = 0, startDate = null, finishDate = null, updatedAt = null)
    val todayText = today.toString()
    return when (watchStatus) {
        WatchStatus.Watching -> entry.copy(startDate = todayText)
        WatchStatus.Completed ->
            entry.copy(episodesWatched = totalEpisodes.coerceAtLeast(0), finishDate = todayText)
        else -> entry
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

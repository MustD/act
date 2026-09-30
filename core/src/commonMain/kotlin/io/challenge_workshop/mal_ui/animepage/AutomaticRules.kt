package io.challenge_workshop.mal_ui.animepage

import io.challenge_workshop.mal_ui.animelist.ListEntry
import io.challenge_workshop.mal_ui.animelist.WatchStatus
import kotlinx.datetime.LocalDate

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
internal fun applyAutomaticRules(
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
internal fun newListEntry(watchStatus: WatchStatus, today: LocalDate, totalEpisodes: Int): ListEntry {
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

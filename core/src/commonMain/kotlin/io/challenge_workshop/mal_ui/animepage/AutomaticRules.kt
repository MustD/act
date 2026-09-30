package io.challenge_workshop.mal_ui.animepage

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
 * - Watch Status set to Completed → progress set to the total.
 */
internal fun applyAutomaticRules(
    before: MyListStatus,
    requested: MyListStatus,
    today: LocalDate,
    totalEpisodes: Int,
): MyListStatus {
    if (totalEpisodes <= 0) return requested
    var status = requested
    val todayText = today.toString()

    if (before.watchStatus == WatchStatus.PlanToWatch && requested.watchStatus == WatchStatus.PlanToWatch &&
        before.episodesWatched == 0 && requested.episodesWatched > 0
    ) {
        status = status.copy(watchStatus = WatchStatus.Watching, startDate = status.startDate ?: todayText)
    }
    if (requested.episodesWatched == totalEpisodes && before.episodesWatched != totalEpisodes) {
        status = status.copy(watchStatus = WatchStatus.Completed, finishDate = status.finishDate ?: todayText)
    }
    if (requested.watchStatus == WatchStatus.Completed && before.watchStatus != WatchStatus.Completed) {
        status = status.copy(episodesWatched = totalEpisodes)
    }
    return status
}

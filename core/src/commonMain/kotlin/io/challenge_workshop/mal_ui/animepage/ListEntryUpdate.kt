package io.challenge_workshop.mal_ui.animepage

import io.challenge_workshop.mal_ui.animelist.WatchStatus
import kotlinx.datetime.LocalDate

/**
 * What one `PATCH /anime/{id}/my_list_status` changes. A null field is **left out of the request**,
 * and MAL keeps whatever it holds for a field that is left out.
 *
 * Dates need a third state beyond "keep" and "set", so they are [DateUpdate]s and not a nullable
 * date: clearing one is sending it *empty*, and that is a different request from not mentioning it.
 */
data class ListEntryUpdate(
    val watchStatus: WatchStatus? = null,
    /** 0–10; 0 is "no score". */
    val score: Int? = null,
    val episodesWatched: Int? = null,
    val startDate: DateUpdate? = null,
    val finishDate: DateUpdate? = null,
) {
    /** A PATCH with no fields changes nothing but `updated_at`, so none is worth sending. */
    val isEmpty: Boolean
        get() = watchStatus == null && score == null && episodesWatched == null &&
            startDate == null && finishDate == null
}

/**
 * A change to a date field. A [Set] holds a real [LocalDate] and never a string: an impossible date
 * such as `2024-02-30` is silently stored as *cleared* with a 200, so only a date that exists is
 * ever formatted onto the wire (`docs/mal-api/list-status-probe.md#dates`).
 */
sealed interface DateUpdate {
    data object Clear : DateUpdate

    data class Set(val date: LocalDate) : DateUpdate
}

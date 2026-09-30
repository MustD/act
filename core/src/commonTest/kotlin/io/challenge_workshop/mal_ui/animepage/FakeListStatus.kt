package io.challenge_workshop.mal_ui.animepage

import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters

/** How the fake MAL answers one `PATCH /anime/{id}/my_list_status`. */
sealed interface ListStatusResponse {
    data class Saved(val status: FakeListStatus) : ListStatusResponse

    data class Failure(val status: HttpStatusCode = HttpStatusCode.BadRequest) : ListStatusResponse

    data object TransportFailure : ListStatusResponse
}

/**
 * MAL's whole-entry answer to a PATCH, as raw JSON for the reason [FakeAnimeDetails] is. Progress is
 * `num_episodes_watched` here although the request calls it `num_watched_episodes`.
 */
data class FakeListStatus(
    val watchStatus: String = "watching",
    val score: Int = 0,
    val watched: Int = 0,
    val startDate: String? = null,
    val finishDate: String? = null,
) {
    fun json(): String = buildString {
        append("""{"status":"$watchStatus","score":$score,"num_episodes_watched":$watched""")
        startDate?.let { append(""","start_date":"$it"""") }
        finishDate?.let { append(""","finish_date":"$it"""") }
        append(""","is_rewatching":false,"priority":0,"num_times_rewatched":0,"tags":[],"comments":"",""")
        append(""""updated_at":"2026-09-30T12:00:00+00:00"}""")
    }

    /**
     * What MAL would hold after [form]: fields it names are replaced, a present-but-empty date is
     * cleared, and progress is clamped to `0…total` when the total is known — the behaviours the
     * probe found.
     */
    fun applying(form: Parameters, totalEpisodes: Int = 0): FakeListStatus {
        fun date(name: String, old: String?) = form[name]?.ifEmpty { null } ?: old.takeIf { name !in form }
        val watchedNow = form["num_watched_episodes"]?.toInt()?.let {
            if (totalEpisodes > 0) it.coerceIn(0, totalEpisodes) else it.coerceAtLeast(0)
        } ?: watched
        return copy(
            watchStatus = form["status"] ?: watchStatus,
            score = form["score"]?.toInt() ?: score,
            watched = watchedNow,
            startDate = date("start_date", startDate),
            finishDate = date("finish_date", finishDate),
        )
    }
}

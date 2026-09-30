package io.challenge_workshop.mal_ui.animepage

import io.ktor.http.HttpStatusCode

/** How the fake MAL answers one `GET /anime/{id}`. */
sealed interface AnimeDetailsResponse {
    data class Found(val anime: FakeAnimeDetails) : AnimeDetailsResponse

    data object NotFound : AnimeDetailsResponse

    data class Failure(val status: HttpStatusCode = HttpStatusCode.ServiceUnavailable) : AnimeDetailsResponse

    data object TransportFailure : AnimeDetailsResponse
}

/**
 * MAL's flat `GET /anime/{id}` shape, as raw JSON for the reason `FakeEntry` is: the production wire
 * types are half of what is under test.
 *
 * A [watchStatus] of null leaves `my_list_status` off entirely, which is what MAL does for an anime
 * that is not on the list. An unset date is an absent key, not `null` — see `list-status-probe.md`.
 */
data class FakeAnimeDetails(
    val id: Long,
    val title: String,
    val synopsis: String? = "A synopsis.",
    val numEpisodes: Int = 26,
    val mediaType: String = "tv",
    val airingStatus: String = "finished_airing",
    val watchStatus: String? = "watching",
    val score: Int = 8,
    val watched: Int = 3,
    val startDate: String? = null,
    val finishDate: String? = null,
    val updatedAt: String = "2026-09-01T12:00:00+00:00",
    val related: List<FakeRelated> = emptyList(),
) {
    fun json(): String = buildString {
        append("""{"id":$id,"title":"$title",""")
        append(""""main_picture":{"medium":"https://cdn.myanimelist.net/images/anime/4/$id.jpg",""")
        append(""""large":"https://cdn.myanimelist.net/images/anime/4/${id}l.jpg"},""")
        synopsis?.let { append(""""synopsis":"$it",""") }
        append(""""num_episodes":$numEpisodes,"media_type":"$mediaType","status":"$airingStatus"""")
        if (watchStatus != null) {
            append(""","my_list_status":{"status":"$watchStatus","score":$score,"num_episodes_watched":$watched""")
            startDate?.let { append(""","start_date":"$it"""") }
            finishDate?.let { append(""","finish_date":"$it"""") }
            append(""","is_rewatching":false,"updated_at":"$updatedAt"}""")
        }
        append(""","related_anime":[${related.joinToString(",") { it.json() }}]}""")
    }
}

/**
 * One `related_anime` element. [onListAs] is the node's `my_list_status.status`; null leaves the key
 * off, which is what MAL does for an anime that is not on the list.
 */
data class FakeRelated(
    val id: Long,
    val title: String,
    val relation: String = "Sequel",
    val onListAs: String? = null,
    val withPicture: Boolean = true,
) {
    fun json(): String = buildString {
        append("""{"node":{"id":$id,"title":"$title"""")
        if (withPicture) append(""","main_picture":{"medium":"https://cdn.myanimelist.net/images/anime/9/$id.jpg"}""")
        if (onListAs != null) {
            append(""","my_list_status":{"status":"$onListAs","score":0,"num_episodes_watched":0,"is_rewatching":false}""")
        }
        append("""},"relation_type":"sequel","relation_type_formatted":"$relation"}""")
    }
}

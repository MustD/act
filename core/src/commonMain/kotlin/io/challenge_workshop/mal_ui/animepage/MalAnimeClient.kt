package io.challenge_workshop.mal_ui.animepage

import io.challenge_workshop.mal_ui.animelist.AiringStatus
import io.challenge_workshop.mal_ui.animelist.Anime
import io.challenge_workshop.mal_ui.animelist.AnimePicture
import io.challenge_workshop.mal_ui.animelist.ListEntry
import io.challenge_workshop.mal_ui.animelist.ListStatusBody
import io.challenge_workshop.mal_ui.mal.decodeOrThrow
import io.challenge_workshop.mal_ui.mal.reachMalApi
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.parameters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET /anime/{id}` and `PATCH /anime/{id}/my_list_status`. HTTP and parsing only, handed the caller's
 * **authenticated** client for the reason [io.challenge_workshop.mal_ui.animelist.MalAnimeListClient]
 * is.
 */
class MalAnimeClient(
    private val apiBaseUrl: String,
    private val http: HttpClient,
) {
    suspend fun anime(animeId: Long): AnimeDetails {
        val response = reachMalApi {
            http.get("${apiBaseUrl.trimEnd('/')}/anime/$animeId") {
                parameter("fields", ANIME_PAGE_FIELDS)
            }
        }
        return response.decodeOrThrow<AnimeDetailsBody>().toDetails()
    }

    /**
     * Changes the user's List Entry for [animeId] and returns MAL's **whole entry after the write**.
     *
     * That answer, not the request, is the new confirmed value: MAL clamps progress and rounds a
     * score without any error. The same call creates the entry when the anime is not on the list.
     */
    suspend fun updateListEntry(animeId: Long, update: ListEntryUpdate): ListEntry {
        val form = parameters {
            update.watchStatus?.wireValue?.let { append("status", it) }
            update.score?.let { append("score", it.toString()) }
            // Named `num_watched_episodes` here and `num_episodes_watched` in every response.
            update.episodesWatched?.let { append("num_watched_episodes", it.toString()) }
            update.startDate?.let { append("start_date", it.wire()) }
            update.finishDate?.let { append("finish_date", it.wire()) }
        }
        val response = reachMalApi {
            http.patch("${apiBaseUrl.trimEnd('/')}/anime/$animeId/my_list_status") {
                setBody(FormDataContent(form))
            }
        }
        return response.decodeOrThrow<ListStatusBody>().toListEntry()
    }

    private fun DateUpdate.wire(): String = when (this) {
        DateUpdate.Clear -> ""
        is DateUpdate.Set -> date.toString()
    }

    companion object {
        /**
         * What the Anime Page draws, its Related Anime included: the page's own fetch is also where
         * each one's "already on your list" comes from, so no follow-up request is made for it.
         *
         * `my_list_status` takes **no** sub-field selection — `my_list_status{start_date}` puts the
         * field on the anime instead — and already includes both dates when they are set. The braces
         * after `related_anime` select fields of its `node`. Both from `docs/mal-api/list-status-probe.md`.
         * MAL answers 200 to a wrong string and just omits data, so the test asserts this one.
         */
        const val ANIME_PAGE_FIELDS: String =
            "id,title,main_picture,synopsis,num_episodes,media_type,status,my_list_status," +
                "related_anime{my_list_status}"
    }
}

/** `GET /anime/{id}` is flat: the anime's own fields, with `my_list_status` beside them. */
@Serializable
internal data class AnimeDetailsBody(
    val id: Long = 0,
    val title: String = "",
    @SerialName("main_picture") val mainPicture: AnimePicture? = null,
    val synopsis: String? = null,
    @SerialName("num_episodes") val numEpisodes: Int = 0,
    @SerialName("media_type") val mediaType: String? = null,
    /** The **Airing** Status; the Watch Status is inside `my_list_status`. */
    val status: AiringStatus = AiringStatus.Unknown,
    @SerialName("my_list_status") val myListStatus: ListStatusBody? = null,
    @SerialName("related_anime") val relatedAnime: List<RelatedAnimeBody> = emptyList(),
) {
    fun toDetails(): AnimeDetails = AnimeDetails(
        anime = Anime(
            animeId = id,
            title = title,
            picture = mainPicture,
            totalEpisodes = numEpisodes,
            mediaType = mediaType,
            airingStatus = status,
        ),
        synopsis = synopsis,
        listEntry = myListStatus?.toListEntry(),
        related = relatedAnime.map { it.toRelated() },
    )
}

/** One element of `related_anime`: the anime is under `node`, the relation beside it. */
@Serializable
internal data class RelatedAnimeBody(
    val node: RelatedNodeBody = RelatedNodeBody(),
    @SerialName("relation_type_formatted") val relationTypeFormatted: String = "",
) {
    fun toRelated(): RelatedAnime = RelatedAnime(
        animeId = node.id,
        title = node.title,
        picture = node.mainPicture,
        relation = relationTypeFormatted,
        // The key is absent for an anime that is not on the list.
        onList = node.myListStatus != null,
    )
}

@Serializable
internal data class RelatedNodeBody(
    val id: Long = 0,
    val title: String = "",
    @SerialName("main_picture") val mainPicture: AnimePicture? = null,
    @SerialName("my_list_status") val myListStatus: ListStatusBody? = null,
)

internal fun ListStatusBody.toListEntry(): ListEntry = ListEntry(
    watchStatus = status,
    score = score,
    episodesWatched = numEpisodesWatched,
    startDate = startDate,
    finishDate = finishDate,
    updatedAt = updatedAt,
)

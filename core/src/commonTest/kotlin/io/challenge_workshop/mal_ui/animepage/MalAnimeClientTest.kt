package io.challenge_workshop.mal_ui.animepage

import io.challenge_workshop.mal_ui.animelist.AiringStatus
import io.challenge_workshop.mal_ui.animelist.WatchStatus
import io.challenge_workshop.mal_ui.mal.MalAuthException
import io.challenge_workshop.mal_ui.mal.malClientDefaults
import io.challenge_workshop.mal_ui.session.FakeMal
import io.challenge_workshop.mal_ui.session.TEST_API_BASE_URL
import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * `GET /anime/{id}`. As for the list, the **outgoing request is asserted**: a wrong `fields` string
 * still answers 200 and costs the page its data with no error anywhere.
 */
class MalAnimeClientTest {

    private fun clientOver(mal: FakeMal): MalAnimeClient {
        val http = HttpClient(mal.engine) {
            malClientDefaults()
            install(DefaultRequest) { headers.append(HttpHeaders.Authorization, "Bearer good-access") }
        }
        return MalAnimeClient(TEST_API_BASE_URL, http)
    }

    private fun mal(response: (Long) -> AnimeDetailsResponse) =
        FakeMal(acceptedAccessToken = "good-access", animeDetails = response)

    @Test
    fun the_request_asks_for_the_agreed_fields_on_the_anime_path() = runTest {
        val mal = mal { AnimeDetailsResponse.Found(FakeAnimeDetails(it, "Trigun")) }

        clientOver(mal).anime(6)

        val url = mal.animeDetailsRequests.single()
        assertEquals("/v2/anime/6", url.encodedPath)
        assertEquals(MalAnimeClient.ANIME_PAGE_FIELDS, url.parameters["fields"])
        assertEquals(listOf("fields"), url.parameters.names().toList(), "nothing else rides along")
        // The braces are what puts `my_list_status` on each Related Anime, and the two dates are
        // already inside a top-level `my_list_status` — asking for them by name misfires.
        assertEquals(
            "id,title,main_picture,synopsis,num_episodes,media_type,status,my_list_status," +
                "related_anime{my_list_status}",
            MalAnimeClient.ANIME_PAGE_FIELDS,
        )
    }

    @Test
    fun a_listed_anime_parses_with_its_dates() = runTest {
        val mal = mal {
            AnimeDetailsResponse.Found(
                FakeAnimeDetails(it, "Trigun", watchStatus = "completed", score = 9, watched = 26,
                    startDate = "2024-03", finishDate = "2024-04-02"),
            )
        }

        val anime = clientOver(mal).anime(6)

        assertEquals(6, anime.animeId)
        assertEquals("Trigun", anime.title)
        assertEquals("A synopsis.", anime.synopsis)
        assertEquals(26, anime.totalEpisodes)
        assertEquals(AiringStatus.FinishedAiring, anime.airingStatus)
        val status = anime.listStatus!!
        assertEquals(WatchStatus.Completed, status.watchStatus)
        assertEquals(9, status.score)
        assertEquals(26, status.episodesWatched)
        assertEquals("2024-03", status.startDate, "a partial date is kept as MAL sent it")
        assertEquals("2024-04-02", status.finishDate)
    }

    @Test
    fun an_anime_not_on_the_list_has_no_list_status_and_unset_dates_are_null() = runTest {
        val mal = mal { AnimeDetailsResponse.Found(FakeAnimeDetails(it, "Trigun", watchStatus = null, synopsis = null)) }

        val anime = clientOver(mal).anime(6)

        assertNull(anime.listStatus)
        assertNull(anime.synopsis)
    }

    @Test
    fun an_unset_date_is_null_rather_than_an_error() = runTest {
        val mal = mal { AnimeDetailsResponse.Found(FakeAnimeDetails(it, "Trigun")) }

        val status = clientOver(mal).anime(6).listStatus!!

        assertNull(status.startDate)
        assertNull(status.finishDate)
    }

    @Test
    fun a_refusal_and_a_transport_failure_both_surface_as_mal_exceptions() = runTest {
        val refused = mal { AnimeDetailsResponse.NotFound }
        assertEquals(404, assertFailsWith<MalAuthException> { clientOver(refused).anime(6) }.status)

        val offline = mal { AnimeDetailsResponse.TransportFailure }
        val e = assertFailsWith<MalAuthException> { clientOver(offline).anime(6) }
        assertEquals(true, e.message!!.contains("Could not reach the MAL API"))
    }
}

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
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
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

    private fun malSaving(response: (Long, Parameters) -> ListStatusResponse) =
        FakeMal(acceptedAccessToken = "good-access", updateListEntry = response)

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

        val details = clientOver(mal).anime(6)

        assertEquals(6, details.anime.animeId)
        assertEquals("Trigun", details.anime.title)
        assertEquals("A synopsis.", details.synopsis)
        assertEquals(26, details.anime.totalEpisodes)
        assertEquals(AiringStatus.FinishedAiring, details.anime.airingStatus)
        val status = details.listEntry!!
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

        assertNull(anime.listEntry)
        assertNull(anime.synopsis)
    }

    @Test
    fun an_unset_date_is_null_rather_than_an_error() = runTest {
        val mal = mal { AnimeDetailsResponse.Found(FakeAnimeDetails(it, "Trigun")) }

        val status = clientOver(mal).anime(6).listEntry!!

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

    @Test
    fun an_update_is_a_form_encoded_patch_on_the_my_list_status_path() = runTest {
        val mal = malSaving { _, _ -> ListStatusResponse.Saved(FakeListStatus()) }

        clientOver(mal).updateListEntry(
            6,
            ListEntryUpdate(
                watchStatus = WatchStatus.OnHold,
                score = 7,
                episodesWatched = 12,
                startDate = DateUpdate.Set(LocalDate(2024, 3, 5)),
            ),
        )

        val patch = mal.listStatusPatches.single()
        assertEquals(HttpMethod.Patch, patch.method)
        assertEquals("/v2/anime/6/my_list_status", patch.url.encodedPath)
        assertEquals("application/x-www-form-urlencoded", patch.contentType?.substringBefore(";"))
        // `num_watched_episodes` in the request, `num_episodes_watched` in every response: a client
        // written by symmetry sends a field MAL ignores and still gets a 200.
        assertEquals(
            mapOf(
                "status" to listOf("on_hold"),
                "score" to listOf("7"),
                "num_watched_episodes" to listOf("12"),
                "start_date" to listOf("2024-03-05"),
            ),
            patch.form.entries().associate { it.key to it.value },
        )
    }

    @Test
    fun a_field_left_out_of_the_update_is_left_out_of_the_request() = runTest {
        val mal = malSaving { _, _ -> ListStatusResponse.Saved(FakeListStatus()) }

        clientOver(mal).updateListEntry(6, ListEntryUpdate(score = 0))

        assertEquals(mapOf("score" to listOf("0")), mal.listStatusPatches.single().form.entries().associate { it.key to it.value })
    }

    @Test
    fun clearing_a_date_sends_the_field_empty() = runTest {
        val mal = malSaving { _, _ -> ListStatusResponse.Saved(FakeListStatus()) }

        clientOver(mal).updateListEntry(6, ListEntryUpdate(finishDate = DateUpdate.Clear))

        val form = mal.listStatusPatches.single().form
        assertEquals(listOf(""), form.getAll("finish_date"), "present but empty is what clears")
        assertEquals(setOf("finish_date"), form.names())
    }

    @Test
    fun the_response_is_the_new_confirmed_entry_including_what_mal_changed() = runTest {
        val mal = malSaving { _, form ->
            ListStatusResponse.Saved(FakeListStatus(watched = 26, startDate = "2024-03").applying(form, 26))
        }

        val status = clientOver(mal).updateListEntry(6, ListEntryUpdate(episodesWatched = 27))

        assertEquals(26, status.episodesWatched, "MAL clamped it, and that is the value to believe")
        assertEquals("2024-03", status.startDate)
        assertNull(status.finishDate)
    }

    @Test
    fun a_refused_update_and_a_transport_failure_surface_as_mal_exceptions() = runTest {
        val refused = malSaving { _, _ -> ListStatusResponse.Failure() }
        assertEquals(
            400,
            assertFailsWith<MalAuthException> {
                clientOver(refused).updateListEntry(
                    6,
                    ListEntryUpdate(score = 3)
                )
            }.status,
        )

        val offline = malSaving { _, _ -> ListStatusResponse.TransportFailure }
        val e = assertFailsWith<MalAuthException> { clientOver(offline).updateListEntry(6, ListEntryUpdate(score = 3)) }
        assertEquals(true, e.message!!.contains("Could not reach the MAL API"))
    }
}

@file:OptIn(ExperimentalTestApi::class)

package io.challenge_workshop.mal_ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import io.challenge_workshop.mal_ui.animelist.AiringStatus
import io.challenge_workshop.mal_ui.animelist.WatchStatus
import io.challenge_workshop.mal_ui.animepage.AnimePage
import io.challenge_workshop.mal_ui.animepage.AnimePageHistory
import io.challenge_workshop.mal_ui.animepage.AnimePageLoad
import io.challenge_workshop.mal_ui.animepage.MyListStatus
import io.challenge_workshop.mal_ui.auth.ANIME_LIST_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_BACK_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_CLOSE_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_ERROR_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_LOADING_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_SYNOPSIS_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_TAG
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Anime Page as drawn: what each load state shows, and that the controls are wired. What opening
 * and closing *cause* is `AnimePageRepositoryTest`'s.
 */
class AnimePageScreenTest {

    private fun page(
        id: Long = 1,
        load: AnimePageLoad = AnimePageLoad.Loading,
        synopsis: String? = null,
        listStatus: MyListStatus? = MyListStatus(WatchStatus.Watching, 8, 3, null, null, null),
    ) = AnimePage(
        animeId = id,
        title = "Cowboy Bebop",
        picture = null,
        totalEpisodes = 26,
        mediaType = "tv",
        airingStatus = AiringStatus.FinishedAiring,
        listStatus = listStatus,
        synopsis = synopsis,
        load = load,
    )

    private fun stateWith(vararg pages: AnimePage) = signedIn(animePages = AnimePageHistory(pages.toList()))

    @Test
    fun an_open_page_replaces_the_list() {
        runComposeUiTest {
            setContent { SessionRoute(stateWith(page()), RecordedActions().actions) }

            onNodeWithTag(ANIME_PAGE_TAG).assertIsDisplayed()
            onAllNodesWithTag(ANIME_LIST_TAG).assertCountEquals(0)
        }
    }

    @Test
    fun a_loading_page_shows_what_it_opened_with_and_a_placeholder_for_the_rest() {
        runComposeUiTest {
            setContent { SessionRoute(stateWith(page()), RecordedActions().actions) }

            onNodeWithText("Cowboy Bebop").assertIsDisplayed()
            onNodeWithText("3 / 26").assertIsDisplayed()
            onNodeWithTag(ANIME_PAGE_LOADING_TAG).assertIsDisplayed()
            onAllNodesWithTag(ANIME_PAGE_SYNOPSIS_TAG).assertCountEquals(0)
        }
    }

    @Test
    fun a_loaded_page_shows_the_synopsis_and_the_dates() {
        runComposeUiTest {
            val loaded = page(
                load = AnimePageLoad.Loaded,
                synopsis = "Bounty hunters in space.",
                listStatus = MyListStatus(WatchStatus.Completed, 9, 26, "2024-03", "2024-04-02", null),
            )
            setContent { SessionRoute(stateWith(loaded), RecordedActions().actions) }

            assertEquals("Bounty hunters in space.", onNodeWithTag(ANIME_PAGE_SYNOPSIS_TAG).textContent())
            onNodeWithText("2024-03", substring = true).assertIsDisplayed()
            onNodeWithText("2024-04-02", substring = true).assertIsDisplayed()
            onAllNodesWithTag(ANIME_PAGE_LOADING_TAG).assertCountEquals(0)
        }
    }

    @Test
    fun a_failed_page_says_why_and_offers_a_retry() {
        runComposeUiTest {
            val recorded = RecordedActions()
            setContent {
                SessionRoute(stateWith(page(load = AnimePageLoad.Failed("MAL is down"))), recorded.actions)
            }

            onNodeWithTag(ANIME_PAGE_ERROR_TAG).assertIsDisplayed()
            onNodeWithText("MAL is down").assertIsDisplayed()
            onNodeWithText("Retry").performClick()

            assertEquals(listOf("animePageRetry"), recorded.calls)
        }
    }

    @Test
    fun an_anime_not_on_the_list_says_so() {
        runComposeUiTest {
            setContent { SessionRoute(stateWith(page(listStatus = null)), RecordedActions().actions) }

            onNodeWithText("Not on your list.").assertIsDisplayed()
        }
    }

    @Test
    fun the_close_button_closes_and_back_appears_only_with_something_to_go_back_to() {
        runComposeUiTest {
            val recorded = RecordedActions()
            setContent { SessionRoute(stateWith(page()), recorded.actions) }

            onAllNodesWithTag(ANIME_PAGE_BACK_TAG).assertCountEquals(0)
            onNodeWithTag(ANIME_PAGE_CLOSE_TAG).performClick()

            assertEquals(listOf("animePageClose"), recorded.calls)
        }
        runComposeUiTest {
            val recorded = RecordedActions()
            setContent { SessionRoute(stateWith(page(1), page(2)), recorded.actions) }

            onNodeWithTag(ANIME_PAGE_BACK_TAG).performClick()

            assertEquals(listOf("animePageBack"), recorded.calls)
        }
    }

    @Test
    fun escape_goes_one_page_back() {
        runComposeUiTest {
            val recorded = RecordedActions()
            setContent { SessionRoute(stateWith(page()), recorded.actions) }
            waitForIdle()

            onNodeWithTag(ANIME_PAGE_TAG).performKeyInput { pressKey(Key.Escape) }

            assertEquals(listOf("animePageBack"), recorded.calls)
        }
    }

    @Test
    fun tapping_a_list_entry_asks_to_open_it() {
        runComposeUiTest {
            val recorded = RecordedActions()
            setContent { SessionRoute(signedIn(), recorded.actions) }

            onNodeWithText("Mushishi").performClick()

            assertEquals(listOf("Mushishi"), recorded.opened.map { it.title })
        }
    }
}

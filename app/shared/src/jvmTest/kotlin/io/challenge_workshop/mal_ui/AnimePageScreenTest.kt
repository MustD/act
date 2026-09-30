@file:OptIn(ExperimentalTestApi::class)

package io.challenge_workshop.mal_ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import io.challenge_workshop.mal_ui.animelist.AnimeListLayout
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_PANEL_TAG
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
import io.challenge_workshop.mal_ui.animepage.ListEdit
import io.challenge_workshop.mal_ui.animepage.MyListStatus
import io.challenge_workshop.mal_ui.animepage.PageSave
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_EPISODES_MINUS_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_EPISODES_PLUS_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_EPISODES_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_FINISH_DATE_CLEAR_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_FINISH_DATE_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_SAVE_ERROR_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_SAVING_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_SCORE_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_START_DATE_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_STATUS_TAG
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performImeAction
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
        save: PageSave = PageSave(),
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
        save = save,
    )

    private fun stateWith(vararg pages: AnimePage) = signedIn(animePages = AnimePageHistory(pages.toList()))

    @Test
    fun below_840dp_an_open_page_replaces_the_list() {
        runComposeUiTest {
            setContent { Box(Modifier.width(839.dp)) { SessionRoute(stateWith(page()), RecordedActions().actions) } }

            onNodeWithTag(ANIME_PAGE_TAG).assertIsDisplayed()
            onAllNodesWithTag(ANIME_LIST_TAG).assertCountEquals(0)
            onAllNodesWithTag(ANIME_PAGE_PANEL_TAG).assertCountEquals(0)
        }
    }

    @Test
    fun from_840dp_an_open_page_sits_beside_the_list_at_480dp() {
        runComposeUiTest {
            setContent { Box(Modifier.width(840.dp)) { SessionRoute(stateWith(page()), RecordedActions().actions) } }

            onNodeWithTag(ANIME_PAGE_TAG).assertIsDisplayed()
            onNodeWithTag(ANIME_LIST_TAG).assertIsDisplayed()
            onNodeWithTag(ANIME_PAGE_PANEL_TAG).assertWidthIsEqualTo(480.dp)
        }
    }

    @Test
    fun the_open_entry_is_highlighted_in_both_layouts_and_only_it() {
        for (layout in AnimeListLayout.entries) {
            runComposeUiTest {
                setContent {
                    Box(Modifier.width(1000.dp)) {
                        SessionRoute(
                            signedIn(layout = layout, animePages = AnimePageHistory(listOf(page(id = 2)))),
                            RecordedActions().actions,
                        )
                    }
                }

                onNode(isSelected() and hasText("Mushishi")).assertExists()
                onNode(isSelected() and hasText("Cowboy Bebop")).assertDoesNotExist()
            }
        }
    }

    @Test
    fun narrowing_the_window_keeps_the_history_and_the_list_keeps_its_place() {
        runComposeUiTest {
            var width by mutableStateOf(1000.dp)
            val many = List(60) { "Title $it" }
            setContent {
                Box(Modifier.width(width)) {
                    SessionRoute(
                        signedIn(list = loadedList(titles = many), animePages = AnimePageHistory(listOf(page(id = 1), page(id = 2)))),
                        RecordedActions().actions,
                    )
                }
            }
            onNodeWithTag(ANIME_LIST_TAG).performScrollToIndex(30)
            onNodeWithTag(ANIME_PAGE_BACK_TAG).assertIsDisplayed()

            width = 500.dp
            waitForIdle()
            onAllNodesWithTag(ANIME_LIST_TAG).assertCountEquals(0)
            onNodeWithTag(ANIME_PAGE_BACK_TAG).assertIsDisplayed()

            width = 1000.dp
            waitForIdle()
            onNodeWithTag(ANIME_LIST_TAG).assertIsDisplayed()
            onNodeWithText("Title 30").assertIsDisplayed()
        }
    }

    @Test
    fun a_loading_page_shows_what_it_opened_with_and_a_placeholder_for_the_rest() {
        runComposeUiTest {
            setContent { Box(Modifier.width(500.dp)) { SessionRoute(stateWith(page()), RecordedActions().actions) } }

            onNodeWithText("Cowboy Bebop").assertIsDisplayed()
            onNodeWithText("/ 26").assertIsDisplayed()
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

            // Below the fold now that the List Entry has controls; existing is what is being asserted.
            onNodeWithTag(ANIME_PAGE_ERROR_TAG).assertExists()
            onNodeWithText("MAL is down").assertExists()
            onNodeWithText("Retry").performScrollTo().performClick()

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

    private val editControls = listOf(
        ANIME_PAGE_STATUS_TAG, ANIME_PAGE_EPISODES_MINUS_TAG, ANIME_PAGE_EPISODES_PLUS_TAG, ANIME_PAGE_EPISODES_TAG,
        ANIME_PAGE_SCORE_TAG, ANIME_PAGE_START_DATE_TAG, ANIME_PAGE_FINISH_DATE_TAG,
    )

    @Test
    fun the_controls_are_disabled_until_the_fetch_has_succeeded() {
        for (load in listOf(AnimePageLoad.Loading, AnimePageLoad.Failed("MAL is down"))) {
            runComposeUiTest {
                setContent { SessionRoute(stateWith(page(load = load)), RecordedActions().actions) }

                for (tag in editControls) onNodeWithTag(tag).assertIsNotEnabled()
            }
        }
    }

    @Test
    fun once_loaded_the_controls_are_enabled() {
        runComposeUiTest {
            setContent { SessionRoute(stateWith(page(load = AnimePageLoad.Loaded)), RecordedActions().actions) }

            for (tag in editControls) onNodeWithTag(tag).assertIsEnabled()
        }
    }

    @Test
    fun plus_and_minus_and_the_number_field_report_edits() {
        runComposeUiTest {
            val recorded = RecordedActions()
            setContent { SessionRoute(stateWith(page(load = AnimePageLoad.Loaded)), recorded.actions) }

            onNodeWithTag(ANIME_PAGE_EPISODES_PLUS_TAG).performClick()
            onNodeWithTag(ANIME_PAGE_EPISODES_MINUS_TAG).performClick()
            onNodeWithTag(ANIME_PAGE_EPISODES_TAG).performTextClearance()
            onNodeWithTag(ANIME_PAGE_EPISODES_TAG).performTextInput("12")
            assertEquals(2, recorded.edits.size, "typing alone saves nothing")
            onNodeWithTag(ANIME_PAGE_EPISODES_TAG).performImeAction()

            assertEquals(
                listOf(ListEdit.AddEpisodes(1), ListEdit.AddEpisodes(-1), ListEdit.SetEpisodes(12)),
                recorded.edits,
            )
        }
    }

    @Test
    fun the_score_picker_offers_mals_labels_and_no_score() {
        runComposeUiTest {
            val recorded = RecordedActions()
            setContent { SessionRoute(stateWith(page(load = AnimePageLoad.Loaded)), recorded.actions) }

            onNodeWithTag(ANIME_PAGE_SCORE_TAG).assertTextContains("8 – Very Good")
            onNodeWithTag(ANIME_PAGE_SCORE_TAG).performClick()
            for (label in listOf("10 – Masterpiece", "1 – Appalling", "No score")) onNodeWithText(label).assertExists()
            onNodeWithText("10 – Masterpiece").performClick()

            assertEquals(listOf<ListEdit>(ListEdit.SetScore(10)), recorded.edits)
        }
    }

    @Test
    fun the_watch_status_picker_reports_the_choice() {
        runComposeUiTest {
            val recorded = RecordedActions()
            setContent { SessionRoute(stateWith(page(load = AnimePageLoad.Loaded)), recorded.actions) }

            onNodeWithTag(ANIME_PAGE_STATUS_TAG).performClick()
            // "On hold" is also a filter chip in the list beside the page; the menu item is drawn last.
            onAllNodesWithText("On hold").onLast().performClick()

            assertEquals(listOf<ListEdit>(ListEdit.SetWatchStatus(WatchStatus.OnHold)), recorded.edits)
        }
    }

    @Test
    fun a_date_can_be_cleared_and_only_a_set_one_offers_it() {
        runComposeUiTest {
            val recorded = RecordedActions()
            val status = MyListStatus(WatchStatus.Completed, 9, 26, null, "2024", null)
            setContent {
                SessionRoute(stateWith(page(load = AnimePageLoad.Loaded, listStatus = status)), recorded.actions)
            }

            onAllNodesWithTag(io.challenge_workshop.mal_ui.auth.ANIME_PAGE_START_DATE_CLEAR_TAG).assertCountEquals(0)
            onNodeWithTag(ANIME_PAGE_FINISH_DATE_TAG).assertTextContains("2024")
            onNodeWithTag(ANIME_PAGE_FINISH_DATE_CLEAR_TAG).performClick()

            assertEquals(listOf<ListEdit>(ListEdit.SetFinishDate(null)), recorded.edits)
        }
    }

    @Test
    fun a_pending_change_is_shown_and_marked_as_saving() {
        runComposeUiTest {
            val confirmed = MyListStatus(WatchStatus.Watching, 8, 3, null, null, null)
            val pending = confirmed.copy(episodesWatched = 6, score = 10)
            val saving = page(
                load = AnimePageLoad.Loaded,
                listStatus = confirmed,
                save = PageSave(target = pending, inFlight = confirmed.copy(episodesWatched = 4)),
            )
            setContent { SessionRoute(stateWith(saving), RecordedActions().actions) }

            onNodeWithTag(ANIME_PAGE_SAVING_TAG).assertIsDisplayed()
            onNodeWithTag(ANIME_PAGE_EPISODES_TAG).assertTextContains("6")
            onNodeWithTag(ANIME_PAGE_SCORE_TAG).assertTextContains("10 – Masterpiece")
        }
    }

    @Test
    fun a_refused_save_shows_its_error_and_no_saving_marker() {
        runComposeUiTest {
            setContent {
                SessionRoute(
                    stateWith(page(load = AnimePageLoad.Loaded, save = PageSave(error = "MAL said no"))),
                    RecordedActions().actions,
                )
            }

            onNodeWithTag(ANIME_PAGE_SAVE_ERROR_TAG).assertIsDisplayed()
            onNodeWithText("MAL said no").assertIsDisplayed()
            onAllNodesWithTag(ANIME_PAGE_SAVING_TAG).assertCountEquals(0)
        }
    }
}

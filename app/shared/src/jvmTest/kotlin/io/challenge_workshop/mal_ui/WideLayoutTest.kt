@file:OptIn(ExperimentalTestApi::class)

package io.challenge_workshop.mal_ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import io.challenge_workshop.mal_ui.animelist.WatchStatus
import io.challenge_workshop.mal_ui.animelist.AiringStatus
import io.challenge_workshop.mal_ui.animelist.Anime
import io.challenge_workshop.mal_ui.animelist.ListEntry
import io.challenge_workshop.mal_ui.animepage.AnimePage
import io.challenge_workshop.mal_ui.animepage.AnimePageHistory
import io.challenge_workshop.mal_ui.animepage.AnimePageLoad
import io.challenge_workshop.mal_ui.animepage.Save
import io.challenge_workshop.mal_ui.auth.ANIME_LIST_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_LIST_FILTERS_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_PANEL_TAG
import io.challenge_workshop.mal_ui.auth.SESSION_MENU_BUTTON_TAG
import io.challenge_workshop.mal_ui.auth.SESSION_SIDEBAR_TAG
import io.challenge_workshop.mal_ui.auth.SESSION_USER_NAME_TAG
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The wide layout: the sidebar from 840dp, and the Anime Page panel that animates open beside the list. */
class WideLayoutTest {

    @Test
    fun from_840dp_a_208dp_sidebar_replaces_the_tabs_and_the_more_menu() {
        val actions = RecordedActions()
        runComposeUiTest {
            setContent {
                Box(Modifier.width(840.dp)) { ThemedSessionRoute(signedIn(), actions.actions) }
            }

            onNodeWithTag(SESSION_SIDEBAR_TAG).assertWidthIsEqualTo(208.dp)
            onNodeWithTag(SESSION_USER_NAME_TAG).assertTextEquals("someone@mal:~$ ls")
            onAllNodesWithTag(SESSION_MENU_BUTTON_TAG).assertCountEquals(0)
            // The six statuses are in the sidebar, once each.
            onAllNodesWithTag(ANIME_LIST_FILTERS_TAG).assertCountEquals(1)
            for (text in listOf("watching", "completed", "on_hold", "dropped", "plan_to_watch", "all")) {
                onNode(hasText(text, substring = true) and hasAnyAncestor(hasTestTag(ANIME_LIST_FILTERS_TAG)))
                    .assertIsDisplayed()
            }
            onNodeWithText("// watch status").assertIsDisplayed()
            onNodeWithText("// session").assertIsDisplayed()

            onNodeWithText("completed", substring = true).performClick()
            onNodeWithText("reload").performClick()
            onNodeWithText("sign out").performClick()
            assertEquals(listOf("selectWatchStatus", "reload", "signOut"), actions.calls)
            assertEquals(listOf<WatchStatus?>(WatchStatus.Completed), actions.watchStatuses)
        }
    }

    @Test
    fun the_active_status_is_marked_in_the_sidebar() {
        runComposeUiTest {
            setContent {
                Box(Modifier.width(1000.dp)) {
                    ThemedSessionRoute(
                        signedIn(list = loadedList(watchStatus = WatchStatus.OnHold)),
                        RecordedActions().actions,
                    )
                }
            }
            onNodeWithText("▸ on_hold").assertIsDisplayed()
        }
    }

    @Test
    fun below_840dp_the_sidebar_is_absent_and_the_phone_chrome_stays() {
        runComposeUiTest {
            setContent { Box(Modifier.width(839.dp)) { ThemedSessionRoute(signedIn(), RecordedActions().actions) } }

            onAllNodesWithTag(SESSION_SIDEBAR_TAG).assertCountEquals(0)
            onNodeWithTag(SESSION_MENU_BUTTON_TAG).assertIsDisplayed()
            onNodeWithTag(ANIME_LIST_FILTERS_TAG).assertIsDisplayed()
        }
    }

    @Test
    fun the_side_panel_grows_to_480dp_and_gives_the_width_back_on_close() {
        runComposeUiTest {
            mainClock.autoAdvance = false
            var history by mutableStateOf(AnimePageHistory(emptyList()))
            setContent {
                Box(Modifier.width(1000.dp)) {
                    ThemedSessionRoute(signedIn(animePages = history), RecordedActions().actions)
                }
            }
            onAllNodesWithTag(ANIME_PAGE_PANEL_TAG).assertCountEquals(0)

            history = AnimePageHistory(listOf(openPage()))
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeBy(100)
            val partial = onNodeWithTag(ANIME_LIST_TAG).getBoundsInRoot().width
            mainClock.advanceTimeBy(600)
            val open = onNodeWithTag(ANIME_LIST_TAG).getBoundsInRoot().width
            assertTrue(open < partial, "the list should keep narrowing while the panel opens: $partial then $open")
            onNodeWithTag(ANIME_PAGE_PANEL_TAG).assertWidthIsEqualTo(480.dp)
            assertEquals(1000.dp - 208.dp - 480.dp, open, "list takes the rest beside the sidebar and the panel")

            history = AnimePageHistory(emptyList())
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeBy(100)
            // Still there mid-close: the page is held while the width animates back.
            onNodeWithTag(ANIME_PAGE_PANEL_TAG).assertExists()
            mainClock.advanceTimeBy(600)
            onAllNodesWithTag(ANIME_PAGE_PANEL_TAG).assertCountEquals(0)
        }
    }

    private fun openPage() = AnimePage(
        anime = Anime(1, "Cowboy Bebop", picture = null, totalEpisodes = 26, mediaType = "tv", AiringStatus.FinishedAiring),
        listEntry = ListEntry(WatchStatus.Watching, 8, 3, null, null, null),
        synopsis = null,
        load = AnimePageLoad.Loading,
        save = Save(),
        related = emptyList(),
    )
}

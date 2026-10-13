@file:OptIn(ExperimentalTestApi::class)

package io.challenge_workshop.mal_ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.challenge_workshop.mal_ui.animelist.AiringStatus
import io.challenge_workshop.mal_ui.animelist.Anime
import io.challenge_workshop.mal_ui.animelist.ListEntry
import io.challenge_workshop.mal_ui.animelist.WatchStatus
import io.challenge_workshop.mal_ui.animepage.AnimePage
import io.challenge_workshop.mal_ui.animepage.AnimePageHistory
import io.challenge_workshop.mal_ui.animepage.AnimePageLoad
import io.challenge_workshop.mal_ui.animepage.Save
import io.challenge_workshop.mal_ui.auth.ABOUT_BACK_TAG
import io.challenge_workshop.mal_ui.auth.ABOUT_SCREEN_TAG
import io.challenge_workshop.mal_ui.auth.ABOUT_VERSION_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_LIST_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_PANEL_TAG
import io.challenge_workshop.mal_ui.auth.GITHUB_URL
import io.challenge_workshop.mal_ui.auth.PRIVACY_URL
import io.challenge_workshop.mal_ui.auth.REPORT_ISSUE_URL
import io.challenge_workshop.mal_ui.auth.SESSION_MENU_BUTTON_TAG
import io.challenge_workshop.mal_ui.auth.SESSION_SIDEBAR_TAG
import io.challenge_workshop.mal_ui.auth.SESSION_TOP_BAR_TAG
import io.challenge_workshop.mal_ui.auth.SUPPORT_DEVELOPMENT_URL
import io.challenge_workshop.mal_ui.auth.SUPPORT_MAL_URL
import io.challenge_workshop.mal_ui.auth.SessionScreenTag
import io.challenge_workshop.mal_ui.auth.WELCOME_ABOUT_TAG
import io.challenge_workshop.mal_ui.screen.SignedInScreen
import io.challenge_workshop.mal_ui.screen.SignedOutScreen
import kotlin.test.Test
import kotlin.test.assertEquals

/** The About Screen in its three frames, and the Main Menu items that reach it. */
class AboutScreenTest {

    @Composable
    private fun Recording(opened: MutableList<String>, content: @Composable () -> Unit) =
        CompositionLocalProvider(LocalUriHandler provides RecordingUriHandler(opened), content = content)

    @Test
    fun signed_out_the_welcome_screen_opens_about_and_about_goes_back() {
        val actions = RecordedActions()
        runComposeUiTest {
            setContent { ThemedSessionRoute(signedOut(), actions.actions) }
            onNodeWithTag(WELCOME_ABOUT_TAG).performClick()
            assertEquals(listOf("openAbout"), actions.calls)
        }
        val back = RecordedActions()
        runComposeUiTest {
            setContent { ThemedSessionRoute(signedOut(screen = SignedOutScreen.About), back.actions) }
            onNodeWithTag(ABOUT_SCREEN_TAG).assertIsDisplayed()
            onAllNodesWithTag(SessionScreenTag.SignIn.tag).assertCountEquals(0)
            onNodeWithTag(ABOUT_BACK_TAG).performClick()
            assertEquals(listOf("backFromAbout"), back.calls)
        }
    }

    @Test
    fun signed_out_esc_leaves_about() {
        val actions = RecordedActions()
        runComposeUiTest {
            setContent { ThemedSessionRoute(signedOut(screen = SignedOutScreen.About), actions.actions) }
            waitForIdle()
            onNodeWithTag(ABOUT_SCREEN_TAG).performKeyInput { pressKey(Key.Escape) }
            assertEquals(listOf("backFromAbout"), actions.calls)
        }
    }

    @Test
    fun about_says_the_version_the_target_the_links_and_both_notices() {
        val opened = mutableListOf<String>()
        runComposeUiTest {
            setContent { Recording(opened) { ThemedSessionRoute(signedOut(screen = SignedOutScreen.About), RecordedActions().actions) } }

            onNodeWithTag(ABOUT_VERSION_TAG).assertIsDisplayed()
            onNodeWithText("ACT $ACT_VERSION · ${getPlatform().name}").assertIsDisplayed()
            onNodeWithText("Unofficial", substring = true).assertIsDisplayed()
            onNodeWithText("Anime data provided by MyAnimeList.net").assertIsDisplayed()

            for (link in listOf("GitHub", "Report an issue", "Support MyAnimeList", "Support development", "Privacy policy")) {
                onNodeWithText(link).performClick()
            }
            assertEquals(
                listOf(GITHUB_URL, REPORT_ISSUE_URL, SUPPORT_MAL_URL, SUPPORT_DEVELOPMENT_URL, PRIVACY_URL),
                opened,
            )
        }
    }

    @Test
    fun the_privacy_url_is_the_production_page() {
        assertEquals("https://act.io-workshop.net/privacy", PRIVACY_URL)
        assertEquals("https://github.com/MustD/act", GITHUB_URL)
    }

    @Test
    fun narrow_about_is_full_screen_with_a_back_control_and_the_list_keys_are_off() {
        val actions = RecordedActions()
        runComposeUiTest {
            setContent {
                Box(Modifier.width(400.dp)) {
                    ThemedSessionRoute(signedIn(screen = SignedInScreen.About), actions.actions)
                }
            }
            waitForIdle()
            onNodeWithTag(ABOUT_SCREEN_TAG).assertIsDisplayed()
            onAllNodesWithTag(ANIME_LIST_TAG).assertCountEquals(0)
            onAllNodesWithTag(SESSION_TOP_BAR_TAG).assertCountEquals(0)

            onNodeWithTag(SessionScreenTag.SignedIn.tag).performKeyInput { pressKey(Key.J) }
            assertEquals(emptyList(), actions.calls, "j must not move through a list that is not showing")

            onNodeWithTag(SessionScreenTag.SignedIn.tag).performKeyInput { pressKey(Key.Escape) }
            onNodeWithTag(ABOUT_BACK_TAG).performClick()
            assertEquals(listOf("backToList", "backToList"), actions.calls)
        }
    }

    @Test
    fun wide_about_sits_beside_the_sidebar_and_draws_no_list_prompt_row_or_page_panel() {
        val actions = RecordedActions()
        runComposeUiTest {
            setContent {
                Box(Modifier.width(1000.dp)) {
                    ThemedSessionRoute(
                        signedIn(screen = SignedInScreen.About, animePages = AnimePageHistory(listOf(openPage()))),
                        actions.actions,
                    )
                }
            }
            waitForIdle()
            onNodeWithTag(SESSION_SIDEBAR_TAG).assertIsDisplayed()
            onNodeWithTag(ABOUT_SCREEN_TAG).assertIsDisplayed()
            onNodeWithText("▸ about").assertIsDisplayed()
            onAllNodesWithTag(ABOUT_BACK_TAG).assertCountEquals(0)
            onAllNodesWithTag(ANIME_LIST_TAG).assertCountEquals(0)
            onAllNodesWithTag(SESSION_TOP_BAR_TAG).assertCountEquals(0)
            onAllNodesWithTag(ANIME_PAGE_PANEL_TAG).assertCountEquals(0)

            onNodeWithTag(SessionScreenTag.SignedIn.tag).performKeyInput { pressKey(Key.Escape) }
            assertEquals(listOf("backToList"), actions.calls)
        }
    }

    @Test
    fun a_watch_status_or_reload_picked_beside_about_returns_to_the_list_and_then_acts() {
        val actions = RecordedActions()
        runComposeUiTest {
            setContent {
                Box(Modifier.width(1000.dp)) {
                    ThemedSessionRoute(signedIn(screen = SignedInScreen.About), actions.actions)
                }
            }
            onNodeWithText("completed", substring = true).performClick()
            onNodeWithText("reload").performClick()
            onNodeWithText("sign out").performClick()
            assertEquals(
                listOf("backToList", "selectWatchStatus", "backToList", "reload", "signOut"),
                actions.calls,
            )
            assertEquals(listOf<WatchStatus?>(WatchStatus.Completed), actions.watchStatuses)
        }
    }

    @Test
    fun the_wide_sidebar_opens_about_and_privacy() {
        val actions = RecordedActions()
        val opened = mutableListOf<String>()
        runComposeUiTest {
            setContent {
                Recording(opened) {
                    Box(Modifier.width(1000.dp)) { ThemedSessionRoute(signedIn(), actions.actions) }
                }
            }
            onNodeWithText("about").performClick()
            onNodeWithText("privacy").performClick()
            assertEquals(listOf("openAbout"), actions.calls)
            assertEquals(listOf(PRIVACY_URL), opened)
        }
    }

    @Test
    fun the_narrow_menu_opens_about_and_privacy() {
        val actions = RecordedActions()
        val opened = mutableListOf<String>()
        runComposeUiTest {
            setContent { Recording(opened) { PhoneSessionRoute(signedIn(), actions.actions) } }
            onNodeWithTag(SESSION_MENU_BUTTON_TAG).performClick()
            onNodeWithText("about").performClick()
            onNodeWithTag(SESSION_MENU_BUTTON_TAG).performClick()
            onNodeWithText("privacy").performClick()
            assertEquals(listOf("openAbout"), actions.calls)
            assertEquals(listOf(PRIVACY_URL), opened)
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

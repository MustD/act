@file:OptIn(ExperimentalTestApi::class)

package io.challenge_workshop.mal_ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.foundation.layout.width
import io.challenge_workshop.mal_ui.auth.DIAGNOSTICS_BACK_TAG
import io.challenge_workshop.mal_ui.auth.DIAGNOSTICS_VERSION_TAG
import io.challenge_workshop.mal_ui.auth.SESSION_SIDEBAR_TAG
import io.challenge_workshop.mal_ui.screen.SignedInScreen
import io.challenge_workshop.mal_ui.auth.ANIME_LIST_TAG
import io.challenge_workshop.mal_ui.auth.SESSION_DIAGNOSTICS_TAG
import io.challenge_workshop.mal_ui.auth.SESSION_MENU_BUTTON_TAG
import io.challenge_workshop.mal_ui.auth.SESSION_MENU_TAG
import io.challenge_workshop.mal_ui.auth.SESSION_THEME_TAG
import io.challenge_workshop.mal_ui.auth.SESSION_TOP_BAR_TAG
import io.challenge_workshop.mal_ui.auth.SESSION_USER_NAME_TAG
import io.challenge_workshop.mal_ui.animelist.WatchStatus
import io.challenge_workshop.mal_ui.mal.MalUser
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The prompt row over the signed-in screen, and the Diagnostics Screen its overflow menu opens.
 *
 * It exists because the Anime List **is** the signed-in screen now: everything that was on that
 * screen before — the name, Reload, Sign out, the Diagnostics Screen — had nowhere left to sit, since
 * anywhere below the entries is unreachable on a real account. So what these tests are really about
 * is that the Anime List taking the screen over cost none of them.
 */
class SignedInTopBarTest {

    /**
     * The Anime List taking over the signed-in screen must not cost the things that were on it. The
     * top app bar's overflow menu is where they went.
     *
     * Asserted on the open menu rather than on the screen, because that is now the only place any of
     * them can be — a "Sign out" that were still lying loose on the screen would pass an assertion
     * over the whole tree and mean the rehousing never happened. Sign out is also clicked, since
     * entries wired to the same action would pass every assertion about what is *in* the menu;
     * diagnostics is [diagnostics_opens_the_diagnostics_screen_with_the_panel_and_the_version]'s.
     */
    @Test
    fun the_overflow_menu_carries_the_main_menu() {
        val entries = listOf("diagnostics", "about", "privacy", "sign out")
        val actions = RecordedActions()
        runComposeUiTest {
            setContent { PhoneSessionRoute(signedIn(), actions.actions) }

            // None of the three is on the screen itself: they live behind one button.
            for (entry in entries) {
                onNodeWithText(entry).assertDoesNotExist()
            }

            onNodeWithTag(SESSION_TOP_BAR_TAG).assertIsDisplayed()
            onNodeWithTag(SESSION_MENU_BUTTON_TAG).performClick()
            waitForIdle()

            for (entry in entries) {
                onNodeWithText(entry).assertIsDisplayed()
            }
            assertEquals(
                entries.size,
                onNodeWithTag(SESSION_MENU_TAG).onChildren().fetchSemanticsNodes().size,
                "a fourth entry is something the Anime List did not displace, so it belongs on the " +
                    "screen where the user can see it rather than behind a menu",
            )

            // Reload is not here: it is the Diagnostics Screen's "Reload list".
            onNodeWithText("reload").assertDoesNotExist()

            onNodeWithText("sign out").performClick()

            assertEquals(listOf("signOut"), actions.calls)
        }
    }

    /**
     * The top app bar's whole job besides the menu: say who is signed in.
     *
     * Measured rather than read, because truncation is not in the semantics tree — an ellipsised name
     * and a wrapped one both read back as the same string, and a bar that wrapped a long name would
     * push the list down by however many lines the name happened to need. So the assertion is that a
     * name nobody could fit takes exactly the height a short one does.
     */
    @Test
    fun the_top_app_bar_names_the_user_and_truncates_rather_than_wrapping() {
        fun heightOf(name: String): Dp {
            var height = 0.dp
            runComposeUiTest {
                setContent {
                    ThemedSessionRoute(signedIn(user = MalUser(1, name)), RecordedActions().actions)
                }
                waitForIdle()
                onNodeWithTag(SESSION_USER_NAME_TAG).assertTextContains(name.take(1), substring = true)
                height = onNodeWithTag(SESSION_USER_NAME_TAG).getBoundsInRoot().height
            }
            return height
        }

        val long = "someone-with-a-name-far-too-long-to-fit-in-a-top-app-bar-".repeat(4)

        assertEquals(
            heightOf("someone"),
            heightOf(long),
            "a long name wrapped instead of truncating, so the bar grows with whatever MAL returns",
        )
    }

    /**
     * The prompt row reads like a shell prompt over the slice on screen: the user, then `ls` of the
     * Watch Status's wire key, and `ls` alone for All.
     */
    @Test
    fun the_prompt_names_the_user_and_the_slice_on_screen() {
        runComposeUiTest {
            var watchStatus by mutableStateOf<WatchStatus?>(WatchStatus.OnHold)
            setContent {
                ThemedSessionRoute(
                    signedIn(list = loadedList(watchStatus = watchStatus)),
                    RecordedActions().actions,
                )
            }

            onNodeWithTag(SESSION_USER_NAME_TAG).assertTextEquals("someone@mal:~$ ls on_hold/")

            watchStatus = null
            waitForIdle()

            onNodeWithTag(SESSION_USER_NAME_TAG).assertTextEquals("someone@mal:~$ ls")
        }
    }

    /** The Theme button asks to flip what is showing, and reaches nothing else. */
    @Test
    fun the_theme_button_asks_for_the_opposite_of_what_is_showing() {
        val actions = RecordedActions()
        runComposeUiTest {
            setContent { PhoneSessionRoute(signedIn(), actions.actions) }

            onNodeWithTag(SESSION_THEME_TAG).performClick()

            assertEquals(listOf("toggleTheme"), actions.calls)
            assertEquals(1, actions.themes.size)
        }
    }

    /**
     * A Session refresh is not a navigation. `ScreenState.SignedIn` carries `refreshing` precisely so
     * the screen can say so without being swapped out, and a list that unmounted for it would lose
     * every page the user has scrolled through.
     */
    @Test
    fun a_session_refresh_does_not_blank_the_signed_in_screen() {
        runComposeUiTest {
            var refreshing by mutableStateOf(false)
            setContent { ThemedSessionRoute(signedIn(refreshing = refreshing), RecordedActions().actions) }
            onNodeWithText(FIXTURE_TITLES.first()).assertIsDisplayed()

            refreshing = true
            waitForIdle()

            onNodeWithTag(ANIME_LIST_TAG).assertIsDisplayed()
            onNodeWithText(FIXTURE_TITLES.first()).assertIsDisplayed()
            onNodeWithTag(SESSION_USER_NAME_TAG).assertIsDisplayed()
        }
    }

    // --- The signed-in screen: the Diagnostics Screen ---------------------------------------------

    /**
     * `SessionDebugPanel` is a real tool rather than scaffolding — it is the only way a human ever
     * sees the refresh path execute — so rehousing it must not cost any of its controls. The Screen
     * is where it went; the Main Menu entry is its disclosure.
     */
    @Test
    fun diagnostics_opens_the_diagnostics_screen_with_the_panel_and_the_version() {
        val actions = RecordedActions()
        runComposeUiTest {
            setContent { PhoneSessionRoute(signedIn(), actions.actions) }

            // "Force 401" writes an invalid token into the store, so it must not be a stray tap away.
            onNodeWithText("Force 401").assertDoesNotExist()

            openDiagnostics()
            assertEquals(listOf("openDiagnostics"), actions.calls)
        }
        runComposeUiTest {
            setContent { PhoneSessionRoute(signedIn(screen = SignedInScreen.Diagnostics), actions.actions) }

            onNodeWithTag(SESSION_DIAGNOSTICS_TAG).assertIsDisplayed()
            for (control in listOf("Force 401", "Reload diagnostics", "Reload profile", "Reload list")) {
                onNodeWithText(control).assertIsDisplayed()
            }
            // The name is in the prompt row; the MAL id is the half nobody reads until something is wrong.
            onNodeWithText("MAL id 1", substring = true).assertIsDisplayed()
            onNodeWithTag(DIAGNOSTICS_VERSION_TAG).assertTextContains(ACT_VERSION, substring = true)
            // The list is not drawn, and the back control leaves.
            onNodeWithTag(ANIME_LIST_TAG).assertDoesNotExist()
            onNodeWithTag(DIAGNOSTICS_BACK_TAG).performClick()
            assertEquals("backToList", actions.calls.last())
        }
    }

    /** The panel is loaded when the Screen opens, as a Session that has diagnostics should show them. */
    @Test
    fun the_diagnostics_are_loaded_when_the_screen_opens() {
        val actions = RecordedActions()
        runComposeUiTest {
            setContent { PhoneSessionRoute(signedIn(screen = SignedInScreen.Diagnostics), actions.actions) }
            waitForIdle()
            assertEquals(listOf("reloadDiagnostics"), actions.calls)
        }
    }

    /** Wide: the sidebar stays, `diagnostics` is marked, and the list is not drawn. */
    @Test
    fun wide_diagnostics_keeps_the_sidebar_and_marks_the_item() {
        runComposeUiTest {
            setContent { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(1000.dp)) {
                    ThemedSessionRoute(signedIn(screen = SignedInScreen.Diagnostics), RecordedActions().actions)
                } }
            onNodeWithTag(SESSION_SIDEBAR_TAG).assertIsDisplayed()
            onNodeWithText("\u25b8 diagnostics").assertIsDisplayed()
            onNodeWithTag(SESSION_DIAGNOSTICS_TAG).assertIsDisplayed()
            onNodeWithTag(DIAGNOSTICS_BACK_TAG).assertDoesNotExist()
            onNodeWithTag(ANIME_LIST_TAG).assertDoesNotExist()
        }
    }

    /**
     * The panel's four buttons, each reaching its own action, and the warning that only appears once
     * the access token has been deliberately invalidated.
     *
     * What each button then does is `:core`'s: whether a forced 401 drives exactly one real refresh
     * is `MalSessionRefreshTest.forcing_the_access_token_to_expire_drives_exactly_one_real_refresh`,
     * and that the refresh token survives it is the same file's. What a rendering can say is that the
     * button a person presses is the one wired to it — four buttons on one action would be invisible
     * everywhere else.
     */
    @Test
    fun each_diagnostics_screen_button_reaches_its_own_action() {
        val actions = RecordedActions()
        runComposeUiTest {
            setContent { PhoneSessionRoute(signedIn(diagnostics = TEST_DIAGNOSTICS, screen = SignedInScreen.Diagnostics), actions.actions) }
            waitForIdle()
            actions.calls.clear()

            onNodeWithText("Reload diagnostics").performClick()
            onNodeWithText("Reload profile").performClick()
            onNodeWithText("Force 401").performClick()
            onNodeWithText("Reload list").performClick()

            assertEquals(
                listOf("reloadDiagnostics", "refreshUser", "forceExpireAccessToken", "reload"),
                actions.calls,
            )
            onNodeWithText("deliberately invalidated", substring = true).assertIsDisplayed()
        }
    }
}

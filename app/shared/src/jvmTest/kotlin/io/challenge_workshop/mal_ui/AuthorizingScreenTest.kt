@file:OptIn(ExperimentalTestApi::class)

package io.challenge_workshop.mal_ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import io.challenge_workshop.mal_ui.mal.DESKTOP_REDIRECT_URI
import io.challenge_workshop.mal_ui.auth.SignInPhase
import io.challenge_workshop.mal_ui.auth.SignInState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The waiting page — the user is away on myanimelist.net — and Paste-the-code behind "Having trouble?".
 *
 * Paste-the-code is collapsed because it is needed only when the Redirect Capture fails, and it is
 * still always one tap away: it is the one mechanism that works headless, behind a blocked popup and
 * with no Custom-Tabs browser, and the authorization URL has to be reachable by hand because no
 * platform's browser-opening call reliably reports whether it worked.
 */
class AuthorizingScreenTest {

    /**
     * The authorization URL is the whole of Paste-the-code's first half, and it is long enough that
     * selecting it out of a text field by hand is where people give up. No platform's browser-opening
     * call reports failure, so this button is the only guaranteed way to it.
     */
    @Test
    fun the_authorization_url_can_be_copied_without_selecting_it() {
        val clipboard = RecordingClipboard()
        runComposeUiTest {
            @Suppress("DEPRECATION")
            setContent {
                CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                    ThemedSessionRoute(authorizing(), RecordedActions().actions)
                }
            }

            onNodeWithText("Having trouble?").performClick()
            onNodeWithText("Copy").performClick()

            assertEquals(TEST_AUTHORIZATION_URL, clipboard.getText()?.text)
        }
    }

    /**
     * Paste-the-code's second half: the field and the two buttons beside it.
     *
     * Every outcome lands on the same call the platform Redirect Captures funnel into, so what a
     * paste then does is `SignInTest`'s (in `:core`) — one parser and one set of error
     * messages, whichever way the redirect arrived. What is left here is that the field reports what
     * was typed and the two buttons are not wired to each other's action.
     */
    @Test
    fun paste_the_code_reaches_the_view_model() {
        val actions = RecordedActions()
        val pasted = "$DESKTOP_REDIRECT_URI?code=the-code&state=a-state"
        runComposeUiTest {
            setContent {
                ThemedSessionRoute(
                    authorizing(signIn = SignInState(pastedRedirect = "half a")),
                    actions.actions,
                )
            }

            onNodeWithText("Having trouble?").performClick()
            onNodeWithText("half a").performTextReplacement(pasted)
            onNodeWithText("Complete sign-in").performClick()
            onNodeWithText("Cancel").performClick()

            // First, not only: the field is a controlled input over a literal here, so the value it
            // is handed never changes and Compose re-reports the old one behind the new.
            assertEquals(pasted, actions.pastes.first())
            assertEquals(
                listOf("completeSignIn", "cancelSignIn"),
                actions.calls.filterNot { it == "pastedRedirectChange" },
            )
        }
    }

    @Test
    fun the_trouble_section_is_collapsed_by_default_and_opens_on_tap() {
        runComposeUiTest {
            setContent { ThemedSessionRoute(authorizing(), RecordedActions().actions) }

            onNodeWithText("Waiting for MyAnimeList…").assertIsDisplayed()
            onNodeWithText("Cancel").assertIsDisplayed()
            onNodeWithText("Complete sign-in").assertDoesNotExist()
            onNodeWithText("Copy").assertDoesNotExist()

            onNodeWithText("Having trouble?").performClick()

            onNodeWithText("Complete sign-in").assertIsDisplayed()
            onNodeWithText("Copy").assertIsDisplayed()
        }
    }

    /** Including a capture that failed outright, such as desktop port 18040 being taken. */
    @Test
    fun the_trouble_section_opens_by_itself_when_an_error_is_shown() {
        runComposeUiTest {
            setContent {
                ThemedSessionRoute(
                    authorizing(signIn = SignInState(phase = SignInPhase.Failed("Port 18040 is taken"))),
                    RecordedActions().actions,
                )
            }

            onNodeWithText("Complete sign-in").assertIsDisplayed()
        }
    }

    /** The normal case at runtime: the page is already up, the user closed the section, and a new error arrives. */
    @Test
    fun the_trouble_section_reopens_when_a_different_error_replaces_one_already_seen() {
        runComposeUiTest {
            var state by mutableStateOf(authorizing())
            setContent { ThemedSessionRoute(state, RecordedActions().actions) }
            onNodeWithText("Complete sign-in").assertDoesNotExist()

            state = authorizing(signIn = SignInState(phase = SignInPhase.Failed("The sign-in timed out")))
            onNodeWithText("Complete sign-in").assertIsDisplayed()

            onNodeWithText("Having trouble?").performClick()
            onNodeWithText("Complete sign-in").assertDoesNotExist()

            state = authorizing(signIn = SignInState(phase = SignInPhase.Failed("Port 18040 is taken")))
            onNodeWithText("Complete sign-in").assertIsDisplayed()
        }
    }
}

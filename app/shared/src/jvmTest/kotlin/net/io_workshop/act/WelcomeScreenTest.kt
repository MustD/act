@file:OptIn(ExperimentalTestApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package net.io_workshop.act

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import net.io_workshop.act.theme.ActTheme
import net.io_workshop.act.animelist.AnimeListRepository
import net.io_workshop.act.animepage.AnimePageRepository
import net.io_workshop.act.animelist.LayoutPreference
import net.io_workshop.act.screen.ScreenChoice
import net.io_workshop.act.theme.ThemePreference
import net.io_workshop.act.auth.LoopbackRedirectListener
import net.io_workshop.act.auth.LoopbackRedirectListenerTest
import net.io_workshop.act.auth.MAL_REGISTER_URL
import net.io_workshop.act.auth.PRIVACY_URL
import net.io_workshop.act.auth.WELCOME_ABOUT_TAG
import net.io_workshop.act.auth.WELCOME_PRIVACY_TAG
import net.io_workshop.act.auth.WELCOME_REGISTER_TAG
import net.io_workshop.act.auth.SIGNED_OUT_REASON_TAG
import net.io_workshop.act.auth.SignIn
import net.io_workshop.act.auth.SignInPhase
import net.io_workshop.act.auth.SignInState
import net.io_workshop.act.auth.SessionScreenTag
import net.io_workshop.act.auth.StartupRedirect
import net.io_workshop.act.auth.awaitLoopbackPortFree
import net.io_workshop.act.auth.fakeMal
import net.io_workshop.act.mal.DESKTOP_LOOPBACK_PORT
import net.io_workshop.act.mal.DESKTOP_REDIRECT_URI
import net.io_workshop.act.mal.MalAuthConfig
import net.io_workshop.act.session.FakeKeyValueStore
import net.io_workshop.act.session.JsonTokenStore
import net.io_workshop.act.session.MalSessionRepository
import net.io_workshop.act.session.SessionControls
import net.io_workshop.act.session.SessionState
import net.io_workshop.act.session.SignedOutReason
import net.io_workshop.act.session.authorizationUrlFor
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * The welcome screen: what it says, what it reports, and the one seam in this whole test tree that
 * still needs the real stack behind it.
 *
 * MAL supports only the authorization code grant, so there is no password field here and never will
 * be — the password is typed on myanimelist.net and this app only ever sees a code.
 */
class WelcomeScreenTest {

    /**
     * The seam the channel abstraction rests on, exercised for real: `rememberAuthRedirectChannel()`
     * resolves to this Target's actual — a [LoopbackRedirectListener] that binds 18040 — the click
     * reaches the Sign-in through it, and the browser is opened by the *channel* rather than by the
     * screen. Paste-the-code stays on offer throughout regardless.
     *
     * **The one case in this file that still builds the whole stack**, and the reason it does is that
     * the stack *is* what is being asserted. Everything else here renders a literal, so this one owns
     * its `Dispatchers.setMain`, its repository and its port check rather than making every other
     * case pay for them in a `@BeforeTest`.
     *
     * Rendered through [AppScreen] rather than [SessionRoute], because the channel and the actions
     * record are exactly what that layer wires: a [SessionRoute] given a literal would prove the
     * screen draws and nothing about the capture.
     *
     * The waits are real. Arming binds a socket off the main dispatcher and the browser launch runs
     * off it too, so nothing here completes inside `performClick`.
     *
     * What the listener then does with a redirect is [LoopbackRedirectListenerTest]'s; this test only
     * proves the wiring reaches it.
     */
    @Test
    fun signing_in_arms_this_targets_capture_and_still_offers_paste_the_code() {
        // The modules' operations run on Dispatchers.Main, which the JVM test platform does not provide.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val store = JsonTokenStore(FakeKeyValueStore())
        val repository = MalSessionRepository(
            store,
            initialConfig = MalAuthConfig(clientId = "a-client-id"),
            // Not the default factory: the Anime List loads itself as soon as a Session starts, and
            // a unit test must not make that a real request to myanimelist.net.
            clientFactory = fakeMal(),
        )
        // Built in a scope of its own, cancelled below: `SignIn` is process-scoped and nothing else
        // would end it.
        val signInScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val signIn = SignIn(repository, StartupRedirect.None, signInScope)
        val controls = SessionControls(repository, signInScope)
        // Its own scope, cancelled below: nothing else would ever end the collector that watches the
        // Session, or a page request still in flight when the repository underneath is closed.
        val listScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val animeList = AnimeListRepository(repository, listScope)
        val animePages = AnimePageRepository(repository, animeList, listScope)
        val layout = LayoutPreference(store, listScope)
        val screenChoice = ScreenChoice(repository.state, listScope)
        val theme = ThemePreference(store, listScope)
        val opened = Collections.synchronizedList(mutableListOf<String>())
        try {
            runComposeUiTest {
                setContent {
                    // Otherwise the desktop `UriHandler` really does launch a browser from a unit
                    // test.
                    CompositionLocalProvider(LocalUriHandler provides RecordingUriHandler(opened)) {
                        // `App()` provides the theme in production; `AppScreen` is below it.
                        ActTheme { AppScreen(repository, signIn, controls, animeList, animePages, layout, screenChoice, theme) }
                    }
                }

                onNodeWithText("Sign in with MyAnimeList").performClick()
                waitUntil("the sign-in reaches Authorizing", WAIT_MS) {
                    repository.state.value is SessionState.Authorizing
                }

                val state = repository.state.value as SessionState.Authorizing
                assertEquals(DESKTOP_REDIRECT_URI, state.pending.redirectUri)
                val authorizationUrl = authorizationUrlFor(repository.config.value, state.pending)
                waitUntil("the channel opens the browser", WAIT_MS) {
                    opened.toList() == listOf(authorizationUrl)
                }

                // Paste-the-code is collapsed but always reachable: one tap puts the URL on screen to
                // copy by hand, since no platform's browser-opening call reliably reports whether it
                // worked.
                onNodeWithTag(SessionScreenTag.Authorizing.tag).assertIsDisplayed()
                onNodeWithText("Having trouble?").performClick()
                onNodeWithText(authorizationUrl).assertIsDisplayed()
                onNodeWithText("Redirect URL or authorization code").assertIsDisplayed()

                // Not tidying up: cancelling is the *only* thing that gives 18040 back, and a test
                // that walked away from an armed listener would make the next sign-in — here or in
                // the app — fail to arm. The port check below holds this to it.
                onNodeWithText("Cancel").performClick()
                waitUntil("cancelling leaves Authorizing", WAIT_MS) {
                    repository.state.value !is SessionState.Authorizing
                }
            }
        } finally {
            listScope.cancel()
            signInScope.cancel()
            repository.close()
            Dispatchers.resetMain()
            assertTrue(
                awaitLoopbackPortFree(),
                "Port $DESKTOP_LOOPBACK_PORT was left bound by this test.",
            )
        }
    }

    /**
     * The Signed Out Reason is on screen, in a node of its own, so a person reads why rather than a
     * bare "signed out".
     *
     * That the reasons *differ from each other* is `ScreenStateSourceTest`'s, on every Target. What is
     * left here is that the screen draws whichever one it is handed, which no value can say.
     */
    @Test
    fun the_welcome_page_shows_the_signed_out_reason_it_is_given() {
        for (reason in SignedOutReason.entries - SignedOutReason.NeverSignedIn) {
            val state = signedOut(reason)
            runComposeUiTest {
                setContent { ThemedSessionRoute(state, RecordedActions().actions) }

                assertEquals(state.explanation, onNodeWithTag(SIGNED_OUT_REASON_TAG).textContent(), "$reason")
            }
        }
    }

    /** A first visit has nothing to explain, so there is no line for it. */
    @Test
    fun the_welcome_page_shows_no_reason_for_a_first_visit() {
        runComposeUiTest {
            setContent { ThemedSessionRoute(signedOut(SignedOutReason.NeverSignedIn), RecordedActions().actions) }

            onNodeWithText("Welcome to ACT").assertIsDisplayed()
            onNodeWithText("Anime Control Terminal").assertIsDisplayed()
            onNodeWithTag(SIGNED_OUT_REASON_TAG).assertDoesNotExist()
        }
    }

    @Test
    fun a_build_without_a_client_id_says_so_and_disables_the_button() {
        runComposeUiTest {
            setContent {
                ThemedSessionRoute(
                    signedOut(signIn = SignInState(clientIdMissing = true)),
                    RecordedActions().actions,
                )
            }

            onNodeWithText("This build has no Client ID", substring = true).assertIsDisplayed()
            onNodeWithText("Sign in with MyAnimeList").assertIsNotEnabled()
        }
    }

    @Test
    fun the_welcome_screen_is_not_a_link_list() {
        runComposeUiTest {
            setContent { ThemedSessionRoute(signedOut(), RecordedActions().actions) }

            for (gone in listOf("Support MyAnimeList", "Support development", "Report an issue", "Anime data provided by MyAnimeList.net")) {
                onNodeWithText(gone).assertDoesNotExist()
            }
            onNodeWithText("A MyAnimeList account is required", substring = true).assertIsDisplayed()
            onNodeWithText("Unofficial", substring = true).assertIsDisplayed()
        }
    }

    @Test
    fun the_account_requirement_links_to_registration_and_privacy_opens_the_policy() {
        val opened = mutableListOf<String>()
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalUriHandler provides RecordingUriHandler(opened)) {
                    ThemedSessionRoute(signedOut(), RecordedActions().actions)
                }
            }

            onNodeWithTag(WELCOME_REGISTER_TAG).performClick()
            onNodeWithTag(WELCOME_PRIVACY_TAG).performClick()

            assertEquals(listOf(MAL_REGISTER_URL, PRIVACY_URL), opened)
            assertEquals("https://myanimelist.net/register.php", MAL_REGISTER_URL)
        }
    }

    @Test
    fun the_about_link_asks_for_the_about_screen() {
        val recorded = RecordedActions()
        runComposeUiTest {
            setContent { ThemedSessionRoute(signedOut(), recorded.actions) }

            onNodeWithTag(WELCOME_ABOUT_TAG).performClick()

            assertTrue("openAbout" in recorded.calls)
        }
    }

    /** `:core` decides the failure looks like a dead relay; the screen words it for a user. */
    @Test
    fun the_relay_advice_appears_only_when_the_state_says_so() {
        runComposeUiTest {
            var state by mutableStateOf(signedOut(error = "Failed to fetch"))
            setContent { ThemedSessionRoute(state, RecordedActions().actions) }

            onNodeWithText("try again later", substring = true).assertDoesNotExist()

            state = signedOut(error = "Failed to fetch", errorRelayHint = true)

            onNodeWithText("try again later", substring = true).assertIsDisplayed()
            onNodeWithText("gradlew", substring = true).assertDoesNotExist()
        }
    }

    /** Two error cards, two hints: the advice lands under the error it explains and nowhere else. */
    @Test
    fun the_relay_advice_goes_on_the_card_it_is_about() {
        runComposeUiTest {
            setContent {
                ThemedSessionRoute(
                    signedOut(
                        error = "invalid_grant",
                        signIn = SignInState(phase = SignInPhase.Failed("Failed to fetch")),
                        signInRelayHint = true,
                    ),
                    RecordedActions().actions,
                )
            }

            onNodeWithText("invalid_grant").assertIsDisplayed()
            onNodeWithText("Failed to fetch", substring = true)
                .assertTextContains("try again later", substring = true)
        }
    }
}

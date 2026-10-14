package net.io_workshop.act.auth

import net.io_workshop.act.mal.DESKTOP_LOOPBACK_PORT
import net.io_workshop.act.mal.DESKTOP_REDIRECT_URI
import net.io_workshop.act.mal.MalAuthConfig
import net.io_workshop.act.session.FakeKeyValueStore
import net.io_workshop.act.session.JsonTokenStore
import net.io_workshop.act.session.MalSessionRepository
import net.io_workshop.act.session.SessionState
import net.io_workshop.act.session.SignedOutReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * A denial on myanimelist.net, carried by desktop's real Redirect Capture into the real repository.
 *
 * [LoopbackRedirectListenerTest] pins what the listener hands back, and
 * `SignInTest` what `SignIn` does with a `Received` — but that one drives a
 * fake capture, so a listener that quietly went back to judging the redirect itself would pass both.
 * This is the test that sees the drift: a denial has to end here exactly as it does on web and Android,
 * and exactly as the same redirect pasted by hand.
 */
class LoopbackDenialTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val signInJob = SupervisorJob()

    @AfterTest
    fun tearDown() {
        scope.cancel()
        signInJob.cancel()
        assertTrue(awaitLoopbackPortFree(), "The denial left $DESKTOP_LOOPBACK_PORT bound.")
    }

    @Test
    fun a_denial_signs_out_with_authorization_failed_and_clears_the_pending_authorization() = runBlocking {
        val store = JsonTokenStore(FakeKeyValueStore())
        val repository = MalSessionRepository(
            store,
            initialConfig = MalAuthConfig(
                clientId = "a-client-id",
                redirectUri = DESKTOP_REDIRECT_URI,
                tokenEndpoint = FAKE_MAL_TOKEN_ENDPOINT,
                apiBaseUrl = FAKE_MAL_API_BASE_URL,
            ),
            clientFactory = fakeMal(),
        )
        val signIn = SignIn(repository, StartupRedirect.None, CoroutineScope(signInJob + Dispatchers.Unconfined))
        try {
            signIn.start(LoopbackRedirectListener(scope, launchBrowser = {}), openUri = {})
            // Arming binds a socket on `Dispatchers.IO`, so reaching `Authorizing` is a real wait.
            val pending = withTimeout(WAIT) {
                repository.state.filterIsInstance<SessionState.Authorizing>().first().pending
            }

            val response = loopbackGet("$DESKTOP_REDIRECT_URI?error=access_denied&state=${pending.state}")
            // The browser tab still says what happened, even though the listener no longer decides it.
            assertEquals(200, response.statusCode())

            val signedOut = withTimeout(WAIT) {
                repository.state.filterIsInstance<SessionState.SignedOut>().first()
            }
            assertEquals(SignedOutReason.AuthorizationFailed, signedOut.reason)
            assertNull(
                store.readPending(),
                "MAL said no, so the attempt is over — a verifier left behind would outlive it.",
            )
        } finally {
            signIn.cancel()
            repository.close()
        }
    }

    private companion object {
        val WAIT = 10.seconds
    }
}

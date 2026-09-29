package io.challenge_workshop.mal_ui.auth

import io.challenge_workshop.mal_ui.session.FakeKeyValueStore
import io.challenge_workshop.mal_ui.session.FakeMal
import io.challenge_workshop.mal_ui.session.JsonTokenStore
import io.challenge_workshop.mal_ui.session.MalSessionRepository
import io.challenge_workshop.mal_ui.session.PendingAuthorization
import io.challenge_workshop.mal_ui.session.SessionState
import io.challenge_workshop.mal_ui.session.SignedOutReason
import io.challenge_workshop.mal_ui.session.TEST_CONFIG
import io.challenge_workshop.mal_ui.session.TEST_USER
import io.challenge_workshop.mal_ui.session.VALID_TOKENS
import io.challenge_workshop.mal_ui.session.authorizationUrlFor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val REDIRECT_URI = TEST_CONFIG.redirectUri

/**
 * [SignIn] at its interface, on every Target: which phase runs when, that a captured redirect
 * has no parallel code path of its own, and what ends an attempt.
 *
 * Everything past the exchange is `MalSessionRepository`'s and is tested with it. `SignIn` runs on
 * the test scope it is handed, so there is no `Dispatchers.setMain`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignInTest {

    // ---- every ArmResult × AuthRedirectResult pair ----

    @Test
    fun an_unsupported_channel_never_gets_opened_and_falls_back_to_paste_the_code() = signInTest {
        val channel = RecordingAuthRedirectChannel(armResult = ArmResult.Unsupported)
        val opened = mutableListOf<String>()

        signIn.start(channel, opened::add)

        assertTrue(
            channel.openedUrls.isEmpty() && channel.awaited == 0,
            "An unarmed channel captures nothing, so driving it further would park the login " +
                "behind a redirect that is never coming.",
        )
        // The browser still opens, and the URL is still offered by hand — no platform's
        // browser-opening call reliably reports whether it worked.
        val pending = assertNotNull(store.readPending())
        assertEquals(listOf(authorizationUrlFor(repository.config.value, pending)), opened)
        assertEquals(SessionState.Authorizing(pending), repository.state.value)
        assertEquals(SignInPhase.Idle, signIn.state.value.phase)

        signIn.setPastedRedirect("$REDIRECT_URI?code=the-code&state=${pending.state}")
        signIn.completePasted()

        awaitSignedIn()
    }

    @Test
    fun an_arm_failure_is_reported_before_any_authorization_is_started() = signInTest {
        val channel = RecordingAuthRedirectChannel(armResult = ArmResult.Failed("Port 18040 is already in use."))
        val opened = mutableListOf<String>()

        signIn.start(channel, opened::add)

        assertEquals(SignInPhase.Failed("Port 18040 is already in use."), signIn.state.value.phase)
        assertTrue(channel.openedUrls.isEmpty() && channel.awaited == 0 && opened.isEmpty())
        // The whole reason `arm` is its own phase: nothing is minted for a flow that cannot finish,
        // and the user has not yet approved anything on myanimelist.net.
        assertNull(store.readPending())
        assertTrue(repository.state.value is SessionState.SignedOut, "${repository.state.value}")
        assertTrue(signIn.state.value.canStart)
    }

    @Test
    fun an_armed_channel_opens_the_browser_itself_and_its_redirect_completes_the_login() = signInTest {
        val channel = RecordingAuthRedirectChannel()
        val opened = mutableListOf<String>()

        signIn.start(channel, opened::add)

        val pending = assertNotNull(store.readPending())
        assertEquals(listOf(authorizationUrlFor(repository.config.value, pending)), channel.openedUrls)
        assertTrue(opened.isEmpty(), "An armed channel owns the browser; opening it twice opens two.")
        // A capture listening anywhere but where MAL redirects is a login that hangs.
        assertEquals(listOf(pending.redirectUri), channel.armedWith)
        assertEquals(SignInPhase.AwaitingRedirect, signIn.state.value.phase)

        channel.deliver(AuthRedirectResult.Received("$REDIRECT_URI?code=the-code&state=${pending.state}"))

        awaitSignedIn()
        assertEquals(SignInPhase.Idle, signIn.state.value.phase)
    }

    @Test
    fun a_cancelled_capture_re_enables_sign_in_and_keeps_the_pending_authorization() = signInTest {
        signIn.start(RecordingAuthRedirectChannel(awaitResult = AuthRedirectResult.Cancelled), openUri = {})

        // Cancellation detection is best-effort on Android — a notification or a configuration change
        // reads as one — so destroying the verifier here would break logins that were about to work.
        assertNotNull(store.readPending(), "a redirect that lands later is still good")
        assertTrue(repository.state.value is SessionState.SignedOut, "${repository.state.value}")
        assertEquals(SignInPhase.Idle, signIn.state.value.phase)
        assertTrue(signIn.state.value.canStart, "The sign-in button has to come back after a cancellation.")
    }

    @Test
    fun a_capture_that_breaks_leaves_the_paste_field_in_place() = signInTest {
        signIn.start(
            RecordingAuthRedirectChannel(awaitResult = AuthRedirectResult.Failed("the listener died")),
            openUri = {},
        )

        assertEquals(SignInPhase.Failed("the listener died"), signIn.state.value.phase)
        // Still `Authorizing`, because the user is away on MAL and can paste what they land on.
        assertTrue(repository.state.value is SessionState.Authorizing, "${repository.state.value}")
    }

    /**
     * The desktop listener gives up after five minutes and reports [AuthRedirectResult.Unsupported]
     * rather than a failure. Nothing has gone wrong — the user was slow — so this lands on
     * Paste-the-code silently.
     */
    @Test
    fun a_capture_that_gives_up_falls_back_to_paste_the_code_without_an_error() = signInTest {
        signIn.start(RecordingAuthRedirectChannel(awaitResult = AuthRedirectResult.Unsupported), openUri = {})

        assertEquals(SignInPhase.Idle, signIn.state.value.phase)
        assertNull(signIn.state.value.error, "A capture that timed out is not something to apologise for.")
        assertTrue(repository.state.value is SessionState.Authorizing, "${repository.state.value}")
        assertNotNull(store.readPending())
        signIn.setPastedRedirect("something")
        assertTrue(signIn.state.value.canComplete)
    }

    // ---- one parser, one set of errors ----

    /** A platform channel that quietly grew its own handling of `error=access_denied` is the drift. */
    @Test
    fun a_received_redirect_fails_exactly_as_the_same_paste_would() = signInTest {
        val denied = "$REDIRECT_URI?error=access_denied&error_description=denied"

        signIn.start(RecordingAuthRedirectChannel(awaitResult = AuthRedirectResult.Received(denied)), openUri = {})

        val pasting = another()
        pasting.signIn.start(RecordingAuthRedirectChannel(armResult = ArmResult.Unsupported), openUri = {})
        pasting.signIn.setPastedRedirect(denied)
        pasting.signIn.completePasted()

        assertNotNull(signIn.state.value.error)
        assertEquals(pasting.signIn.state.value.error, signIn.state.value.error)
        assertEquals(pasting.repository.state.value, repository.state.value)
        assertNull(store.readPending(), "MAL said no, so the attempt is over")
    }

    // ---- paste against capture ----

    @Test
    fun a_paste_that_beats_the_capture_lets_the_channel_go() = signInTest {
        val channel = RecordingAuthRedirectChannel()
        signIn.start(channel, openUri = {})
        val pending = assertNotNull(store.readPending())

        // Paste stays available while the user is away, which is the whole point of the phase.
        signIn.setPastedRedirect("$REDIRECT_URI?code=the-code&state=${pending.state}")
        assertTrue(signIn.state.value.canComplete)
        signIn.completePasted()

        awaitSignedIn()
        // On desktop a channel left listening is a bound 18040 that the next sign-in cannot rebind.
        channel.awaitRelease()
        assertEquals("", signIn.state.value.pastedRedirect)
    }

    @Test
    fun a_capture_that_beats_the_paste_finishes_the_sign_in_and_clears_the_field() = signInTest {
        val channel = RecordingAuthRedirectChannel()
        signIn.start(channel, openUri = {})
        val pending = assertNotNull(store.readPending())
        signIn.setPastedRedirect("half a paste")

        channel.deliver(AuthRedirectResult.Received("$REDIRECT_URI?code=the-code&state=${pending.state}"))

        awaitSignedIn()
        assertEquals(SignInState(clientId = TEST_CONFIG.clientId), signIn.state.value)
    }

    @Test
    fun a_paste_that_fails_leaves_the_capture_listening() = signInTest {
        val channel = RecordingAuthRedirectChannel()
        signIn.start(channel, openUri = {})
        val pending = assertNotNull(store.readPending())

        signIn.setPastedRedirect("%%% not a redirect")
        signIn.completePasted()

        assertNotNull(signIn.state.value.error)
        assertFalse(channel.isReleased, "a typo in the paste must not cost the user the capture")

        channel.deliver(AuthRedirectResult.Received("$REDIRECT_URI?code=the-code&state=${pending.state}"))
        awaitSignedIn()
    }

    @Test
    fun paste_and_start_are_refused_while_the_exchange_is_in_flight() = signInTest(holdExchange = true) {
        val channel = RecordingAuthRedirectChannel()
        signIn.start(channel, openUri = {})
        val pending = assertNotNull(store.readPending())

        channel.deliver(AuthRedirectResult.Received("$REDIRECT_URI?code=the-code&state=${pending.state}"))

        assertEquals(SignInPhase.Exchanging, signIn.state.value.phase)
        signIn.setPastedRedirect("anything")
        assertFalse(signIn.state.value.canComplete)
        assertFalse(signIn.state.value.canStart)

        releaseExchange.complete(Unit)
        awaitSignedIn()
    }

    // ---- what ends a Sign-in ----

    @Test
    fun cancel_releases_the_channel_and_keeps_the_pending_authorization() = signInTest {
        val channel = RecordingAuthRedirectChannel()
        signIn.start(channel, openUri = {})

        signIn.cancel()

        channel.awaitRelease()
        assertEquals(SignInPhase.Idle, signIn.state.value.phase)
        assertNotNull(store.readPending())
        assertEquals(SessionState.SignedOut(SignedOutReason.UserSignedOut), repository.state.value)
    }

    @Test
    fun a_new_start_ends_the_old_one() = signInTest {
        val first = RecordingAuthRedirectChannel()
        val second = RecordingAuthRedirectChannel()
        signIn.start(first, openUri = {})

        signIn.start(second, openUri = {})

        first.awaitRelease()
        assertFalse(second.isReleased)
        assertEquals(SignInPhase.AwaitingRedirect, signIn.state.value.phase)
        val pending = assertNotNull(store.readPending())
        second.deliver(AuthRedirectResult.Received("$REDIRECT_URI?code=the-code&state=${pending.state}"))
        awaitSignedIn()
    }

    @Test
    fun a_sign_out_from_elsewhere_ends_the_attempt() = signInTest {
        val channel = RecordingAuthRedirectChannel()
        signIn.start(channel, openUri = {})
        signIn.setPastedRedirect("half a paste")

        repository.signOut()

        channel.awaitRelease()
        assertEquals(SignInPhase.Idle, signIn.state.value.phase)
        assertEquals("", signIn.state.value.pastedRedirect)
    }

    @Test
    fun the_composition_going_away_is_not_an_event_the_sign_in_knows_about() = signInTest {
        val channel = RecordingAuthRedirectChannel()
        signIn.start(channel, openUri = {})

        // Nothing here stands in for a screen leaving: a Sign-in holds no reference to one. What is
        // asserted is that it is still waiting when nothing has happened to it.
        assertFalse(channel.isReleased)
        assertEquals(SignInPhase.AwaitingRedirect, signIn.state.value.phase)
    }

    // ---- the form ----

    @Test
    fun a_failure_is_cleared_by_the_next_keystroke_paste_or_start() = signInTest {
        fun fail() = signIn.start(RecordingAuthRedirectChannel(armResult = ArmResult.Failed("no")), openUri = {})

        fail()
        signIn.setClientId("typed")
        assertEquals(SignInPhase.Idle, signIn.state.value.phase)

        fail()
        signIn.setPastedRedirect("x")
        assertEquals(SignInPhase.Idle, signIn.state.value.phase)

        fail()
        signIn.start(RecordingAuthRedirectChannel(), openUri = {})
        assertEquals(SignInPhase.AwaitingRedirect, signIn.state.value.phase)
    }

    @Test
    fun the_client_id_field_is_prefilled_from_the_build_time_default() = signInTest {
        assertEquals(TEST_CONFIG.clientId, signIn.state.value.clientId)
    }

    @Test
    fun a_client_id_remembered_from_a_previous_launch_replaces_the_prefill() = signInTest(
        seed = { store.writeClientId("remembered-on-this-device") },
    ) {
        // Read from the store, so it cannot be in the config when this object is constructed — the
        // field has to be re-synced once `restore()` has settled it.
        assertEquals("remembered-on-this-device", signIn.state.value.clientId)
    }

    @Test
    fun editing_the_client_id_reaches_the_repository_config() = signInTest {
        signIn.setClientId("  typed-by-hand  ")

        assertEquals("typed-by-hand", repository.config.value.clientId)
    }

    @Test
    fun signing_in_is_blocked_until_a_client_id_is_present() = signInTest {
        signIn.setClientId("")
        assertFalse(signIn.state.value.canStart)

        signIn.start(RecordingAuthRedirectChannel(), openUri = {})
        assertEquals(SignInPhase.Idle, signIn.state.value.phase, "start() without a Client ID must do nothing")

        signIn.setClientId("something")
        assertTrue(signIn.state.value.canStart)
    }

    // ---- startup ----

    @Test
    fun construction_restores_the_session() = signInTest(seed = {
        store.writeSession(VALID_TOKENS, TEST_USER)
    }) {
        assertEquals(SessionState.SignedIn(TEST_USER), repository.state.value)
    }

    @Test
    fun a_launch_carrying_a_redirect_completes_the_sign_in() = signInTest(
        seed = { seedPending() },
        startup = { redirect() },
    ) {
        awaitSignedIn()
        assertNull(store.readPending(), "A spent Pending Authorization must not survive the launch.")
    }

    /**
     * Ordering: `restore()` settles the state from the store, so a redirect completed before it would
     * have its `SignedIn` overwritten by whatever the store said a moment earlier.
     */
    @Test
    fun the_store_is_read_before_the_redirect_is_completed() = signInTest(
        seed = { seedPending() },
        startup = { redirect() },
    ) {
        awaitSignedIn()
        assertTrue(stateWhenConsumed is SessionState.Authorizing, "$stateWhenConsumed")
    }

    @Test
    fun a_launch_carrying_a_code_with_nothing_to_complete_it_says_so() = signInTest(
        startup = { redirect() },
    ) {
        // Silence here reads as "the sign-in button did nothing".
        val error = assertNotNull(signIn.state.value.error)
        assertTrue("no sign-in in progress" in error, error)
        assertEquals(SessionState.SignedOut(SignedOutReason.NeverSignedIn), repository.state.value)
    }

    @Test
    fun a_stale_launch_redirect_is_completed_when_the_platform_says_to_report_it() = signInTest(
        startup = { redirect(reportIfStale = true) },
    ) {
        assertNotNull(signIn.state.value.error)
    }

    @Test
    fun a_stale_launch_redirect_is_discarded_silently_when_the_platform_says_not_to() = signInTest(
        startup = { redirect(reportIfStale = false) },
    ) {
        assertNull(signIn.state.value.error)
        assertEquals(SignInPhase.Idle, signIn.state.value.phase)
        assertEquals(SessionState.SignedOut(SignedOutReason.NeverSignedIn), repository.state.value)
    }

    @Test
    fun an_authorizing_session_completes_a_launch_redirect_that_is_not_reported_if_stale() = signInTest(
        seed = { seedPending() },
        startup = { redirect(reportIfStale = false) },
    ) {
        awaitSignedIn()
        assertNull(store.readPending())
    }

    @Test
    fun an_authorizing_session_completes_a_launch_redirect_that_is_reported_if_stale() = signInTest(
        seed = { seedPending() },
        startup = { redirect(reportIfStale = true) },
    ) {
        awaitSignedIn()
        assertNull(store.readPending())
    }

    @Test
    fun a_launch_carrying_a_denial_fails_exactly_as_the_same_paste_would() = signInTest(
        seed = { seedPending() },
        startup = { redirect("$REDIRECT_URI?error=access_denied&error_description=denied") },
    ) {
        val pasting = another(seed = { seedPending() })
        pasting.signIn.setPastedRedirect("$REDIRECT_URI?error=access_denied&error_description=denied")
        pasting.signIn.completePasted()

        assertNotNull(signIn.state.value.error)
        assertEquals(pasting.signIn.state.value.error, signIn.state.value.error)
        assertEquals(pasting.repository.state.value, repository.state.value)
    }

    @Test
    fun an_ordinary_launch_restores_as_it_always_did() = signInTest {
        assertEquals(SessionState.SignedOut(SignedOutReason.NeverSignedIn), repository.state.value)
        assertNull(signIn.state.value.error)
        assertTrue(signIn.state.value.canStart)
    }

    // ---- harness ----

    private fun redirect(
        raw: String = "$REDIRECT_URI?code=a-code&state=a-state",
        reportIfStale: Boolean = true,
    ) = StartupRedirectValue(raw, reportIfStale)

    /** One app's worth of graph, with a [SignIn] built over it on the test's own scope. */
    private class Fixture(private val testScope: TestScope, holdExchange: Boolean) {
        val store = JsonTokenStore(FakeKeyValueStore())

        /** Completed by a test to let the token exchange finish, so the `Exchanging` phase is visible. */
        val releaseExchange = CompletableDeferred<Unit>().also { if (!holdExchange) it.complete(Unit) }
        val repository = MalSessionRepository(
            store = store,
            initialConfig = TEST_CONFIG,
            clientFactory = FakeMal(releaseRefresh = releaseExchange).clientFactory,
        )
        lateinit var signIn: SignIn

        /** What the repository had settled on by the time the startup redirect was asked for. */
        var stateWhenConsumed: SessionState? = null

        suspend fun seedPending(): PendingAuthorization = store.writePending(
            codeVerifier = "a-code-verifier",
            state = "a-state",
            redirectUri = REDIRECT_URI,
            clientId = TEST_CONFIG.clientId,
        )

        /** Constructs the [SignIn] — which restores and takes the startup redirect — after seeding. */
        fun launch(startup: (Fixture.() -> StartupRedirectValue?)?) {
            signIn = SignIn(
                repository = repository,
                startupRedirect = StartupRedirect {
                    stateWhenConsumed = repository.state.value
                    startup?.invoke(this)
                },
                scope = CoroutineScope(
                    testScope.backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScope.testScheduler),
                ),
            )
        }

        /** A second, independent app — for comparing two routes to the same outcome. */
        suspend fun another(seed: suspend Fixture.() -> Unit = {}): Fixture =
            Fixture(testScope, holdExchange = false).also { it.seed(); it.launch(startup = null) }

        /**
         * Waits for a token exchange to land. `MockEngine` answers on its own dispatcher, so virtual
         * time alone does not get there.
         */
        suspend fun awaitSignedIn() {
            repository.state.first { it is SessionState.SignedIn && it.user != null }
            signIn.state.first { it.phase is SignInPhase.Idle }
        }
    }

    private fun signInTest(
        seed: suspend Fixture.() -> Unit = {},
        startup: (Fixture.() -> StartupRedirectValue?)? = null,
        holdExchange: Boolean = false,
        block: suspend Fixture.() -> Unit,
    ) = runTest {
        val fixture = Fixture(this, holdExchange)
        try {
            fixture.seed()
            fixture.launch(startup)
            fixture.block()
        } finally {
            fixture.repository.close()
        }
    }
}

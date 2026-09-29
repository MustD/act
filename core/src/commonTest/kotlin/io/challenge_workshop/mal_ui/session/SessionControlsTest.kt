package io.challenge_workshop.mal_ui.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [SessionControls] at its interface: each operation's success and failure, the one-at-a-time rule,
 * and that diagnostics are refreshed behind the operations that change what they show.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionControlsTest {

    // ---- each operation ----

    @Test
    fun sign_out_ends_the_session_and_returns_to_idle() = controlsTest {
        controls.signOut()

        assertEquals(SessionOperation.Idle, controls.state.value.operation)
        assertEquals(SessionState.SignedOut(SignedOutReason.UserSignedOut), repository.state.value)
        assertNull(store.readSession())
    }

    @Test
    fun a_failed_sign_out_is_reported_as_the_operations_failure() = controlsTest(failRemove = true) {
        controls.signOut()

        assertEquals(SessionOperation.Failed("store is read-only"), controls.state.value.operation)
    }

    @Test
    fun refresh_user_succeeds_and_reloads_the_diagnostics() = controlsTest {
        assertNull(controls.state.value.diagnostics)

        controls.refreshUser()
        settle()

        assertEquals(SessionOperation.Idle, controls.state.value.operation)
        assertNotNull(controls.state.value.diagnostics, "refreshing the profile must refresh what the panel shows")
        assertEquals(1, mal.tokenEndpointHits, "the profile fetch earns one refresh")
    }

    @Test
    fun a_failed_refresh_user_is_reported_and_leaves_the_diagnostics_alone() = controlsTest(
        mal = FakeMal(refreshResponse = RefreshResponse.TransportFailure),
    ) {
        controls.refreshUser()
        settle()

        val state = controls.state.value
        assertIs<SessionOperation.Failed>(state.operation)
        assertNull(state.diagnostics)
    }

    @Test
    fun reload_diagnostics_fills_them_without_touching_mal() = controlsTest {
        controls.reloadDiagnostics()

        assertEquals(SessionOperation.Idle, controls.state.value.operation)
        assertNotNull(controls.state.value.diagnostics)
        assertEquals(0, mal.userEndpointHits)
    }

    @Test
    fun a_failing_diagnostics_read_is_reported() = controlsTest(failRead = true) {
        controls.reloadDiagnostics()

        assertEquals(SessionOperation.Failed("store is unreadable"), controls.state.value.operation)
    }

    @Test
    fun force_expire_invalidates_the_token_and_reloads_the_diagnostics() = controlsTest {
        controls.forceExpireAccessToken()

        val state = controls.state.value
        assertEquals(SessionOperation.Idle, state.operation)
        assertTrue(assertNotNull(state.diagnostics).accessTokenIsDeliberatelyInvalid)
    }

    @Test
    fun a_failed_force_expire_is_reported() = controlsTest(failWrite = true) {
        controls.forceExpireAccessToken()

        assertEquals(SessionOperation.Failed("store is read-only"), controls.state.value.operation)
    }

    @Test
    fun the_next_operation_clears_the_last_ones_failure() = controlsTest(failRead = true) {
        controls.reloadDiagnostics()
        assertIs<SessionOperation.Failed>(controls.state.value.operation)

        failRead = false
        controls.reloadDiagnostics()

        assertEquals(SessionOperation.Idle, controls.state.value.operation)
    }

    // ---- one at a time ----

    /**
     * Without the rule, whichever job finished first would set `Idle` and clear the spinner of the one
     * still running. Here the profile fetch is held in flight and a second operation is asked for.
     */
    @Test
    fun a_call_while_running_is_dropped() = controlsTest(holdRefresh = true) {
        controls.refreshUser()
        // `MockEngine` answers on its own dispatcher: wait until the request is really held.
        withContext(Dispatchers.Default) { while (mal.tokenEndpointHits == 0) delay(1) }
        assertEquals(SessionOperation.Running, controls.state.value.operation)

        controls.reloadDiagnostics()
        controls.forceExpireAccessToken()
        controls.signOut()
        controls.refreshUser()

        assertNull(controls.state.value.diagnostics, "a dropped reload must not have run")
        assertEquals(SessionState.SignedIn::class, repository.state.value::class, "a dropped sign-out must not have run")
        assertNotNull(store.readSession())
        assertEquals(1, mal.tokenEndpointHits, "a dropped refresh must not have made a second request")
        assertEquals(SessionOperation.Running, controls.state.value.operation)

        releaseRefresh.complete(Unit)
        settle()

        assertNotNull(controls.state.value.diagnostics)
        assertEquals(1, mal.tokenEndpointHits, "the one accepted operation ran exactly once")
    }

    @Test
    fun a_call_after_the_last_one_finished_is_accepted() = controlsTest {
        controls.reloadDiagnostics()
        controls.signOut()

        assertEquals(SessionState.SignedOut(SignedOutReason.UserSignedOut), repository.state.value)
    }

    // ---- harness ----

    private class ThrowingStore(private val fixture: Fixture) : KeyValueStore {
        private val delegate = FakeKeyValueStore()

        override suspend fun read(key: String): String? {
            if (fixture.failRead) throw IllegalStateException("store is unreadable")
            return delegate.read(key)
        }

        override suspend fun write(key: String, value: String) {
            if (fixture.failWrite) throw IllegalStateException("store is read-only")
            delegate.write(key, value)
        }

        override suspend fun remove(key: String) {
            if (fixture.failRemove) throw IllegalStateException("store is read-only")
            delegate.remove(key)
        }
    }

    /** A signed-in Session, and a [SessionControls] over it on the test's own scope. */
    private class Fixture(
        testScope: TestScope,
        val mal: FakeMal,
        val releaseRefresh: CompletableDeferred<Unit>,
    ) {
        var failRead = false
        var failWrite = false
        var failRemove = false

        val store = JsonTokenStore(ThrowingStore(this), clock = FakeClock())
        val repository = MalSessionRepository(
            store = store,
            clock = FakeClock(),
            initialConfig = TEST_CONFIG,
            clientFactory = mal.clientFactory,
        )

        /** Waits for the running operation to finish; virtual time does not reach `MockEngine`. */
        suspend fun settle() {
            controls.state.first { it.operation !is SessionOperation.Running }
        }

        val controls = SessionControls(
            repository = repository,
            scope = CoroutineScope(
                testScope.backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScope.testScheduler),
            ),
        )
    }

    private fun controlsTest(
        mal: FakeMal? = null,
        holdRefresh: Boolean = false,
        failRead: Boolean = false,
        failWrite: Boolean = false,
        failRemove: Boolean = false,
        block: suspend Fixture.() -> Unit,
    ) = runTest {
        val release = CompletableDeferred<Unit>().also { if (!holdRefresh) it.complete(Unit) }
        val fixture = Fixture(this, mal ?: FakeMal(releaseRefresh = release), release)
        try {
            // Stale tokens: `refreshUser` earns a real 401 and refresh, which is what can be held.
            fixture.store.writeSession(if (holdRefresh) STALE_TOKENS else VALID_TOKENS, TEST_USER)
            fixture.repository.restore()
            fixture.failRead = failRead
            fixture.failWrite = failWrite
            fixture.failRemove = failRemove
            fixture.block()
        } finally {
            fixture.repository.close()
        }
    }
}

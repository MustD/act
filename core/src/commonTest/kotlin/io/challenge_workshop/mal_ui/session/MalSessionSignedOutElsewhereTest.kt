package io.challenge_workshop.mal_ui.session

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a Session removed by another tab looks like from this one. */
@OptIn(ExperimentalCoroutinesApi::class)
class MalSessionSignedOutElsewhereTest {

    private class Fixture(
        val kv: FakeKeyValueStore,
        val store: JsonTokenStore,
        val repository: MalSessionRepository,
    )

    private suspend fun TestScope.fixture(mal: FakeMal, withSession: Boolean = true): Fixture {
        val kv = FakeKeyValueStore()
        val store = JsonTokenStore(kv, clock = FakeClock())
        val repository = MalSessionRepository(
            store = store,
            clock = FakeClock(),
            initialConfig = TEST_CONFIG,
            clientFactory = mal.clientFactory,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
        )
        if (withSession) store.writeSession(STALE_TOKENS, TEST_USER)
        return Fixture(kv, store, repository)
    }

    private suspend fun Fixture.removeElsewhere() {
        kv.changeElsewhere(JsonTokenStore.SESSION_KEY, null)
    }

    @Test
    fun a_removal_while_signed_in_drops_to_signed_out_elsewhere() = runTest {
        val f = fixture(FakeMal())
        f.repository.restore()
        runCurrent()

        f.removeElsewhere()
        runCurrent()

        assertEquals(SignedOutReason.SignedOutElsewhere, assertIs<SessionState.SignedOut>(f.repository.state.value).reason)
        f.repository.close()
    }

    @Test
    fun a_removal_while_restoring_changes_nothing() = runTest {
        val f = fixture(FakeMal())
        runCurrent()

        f.removeElsewhere()
        runCurrent()

        assertEquals(SessionState.Restoring, f.repository.state.value)
        f.repository.close()
    }

    @Test
    fun a_removal_while_signed_out_keeps_the_reason() = runTest {
        val f = fixture(FakeMal(), withSession = false)
        f.repository.restore()
        runCurrent()
        val before = f.repository.state.value

        f.removeElsewhere()
        runCurrent()

        assertEquals(before, f.repository.state.value)
        f.repository.close()
    }

    @Test
    fun a_removal_while_authorizing_changes_nothing() = runTest {
        val f = fixture(FakeMal(), withSession = false)
        f.repository.restore()
        f.repository.beginAuthorization()
        runCurrent()
        val before = f.repository.state.value
        assertIs<SessionState.Authorizing>(before)

        f.removeElsewhere()
        runCurrent()

        assertEquals(before, f.repository.state.value)
        f.repository.close()
    }

    @Test
    fun a_new_value_from_elsewhere_changes_nothing() = runTest {
        val f = fixture(FakeMal())
        f.repository.restore()
        runCurrent()
        val before = f.repository.state.value

        f.kv.changeElsewhere(JsonTokenStore.SESSION_KEY, f.kv.entries.getValue(JsonTokenStore.SESSION_KEY))
        runCurrent()

        assertEquals(before, f.repository.state.value)
        f.repository.close()
    }

    @Test
    fun the_next_request_after_signed_out_elsewhere_carries_no_cached_bearer_token() = runTest {
        val mal = FakeMal(acceptedAccessToken = STALE_TOKENS.accessToken)
        val f = fixture(mal)
        f.repository.restore()
        runCurrent()
        f.repository.fetchUser() // Ktor now caches the accepted token

        f.removeElsewhere()
        runCurrent()

        assertTrue(runCatching { f.repository.fetchUser() }.isFailure)
        f.repository.close()
    }

    @Test
    fun a_refresh_racing_a_removal_signs_out_elsewhere_writes_nothing_and_does_not_retry() = runTest {
        val release = CompletableDeferred<Unit>()
        val mal = FakeMal(releaseRefresh = release)
        val f = fixture(mal)
        f.repository.restore()
        runCurrent()

        val call = backgroundScope.launch { runCatching { f.repository.fetchUser() } }
        runCurrent()
        // The tab that signed out removes the record while MAL is still answering the refresh.
        f.kv.entries.remove(JsonTokenStore.SESSION_KEY)
        release.complete(Unit)
        call.join()

        assertEquals(SignedOutReason.SignedOutElsewhere, assertIs<SessionState.SignedOut>(f.repository.state.value).reason)
        assertNull(f.kv.entries[JsonTokenStore.SESSION_KEY], "the fresh pair must not resurrect the Session")
        assertEquals(1, mal.userEndpointHits, "the retry must not go out with the fresh pair")
        f.repository.close()
    }

    @Test
    fun signing_out_during_an_in_flight_refresh_stays_user_signed_out() = runTest {
        val release = CompletableDeferred<Unit>()
        val mal = FakeMal(releaseRefresh = release)
        val f = fixture(mal)
        f.repository.restore()
        runCurrent()

        val call = backgroundScope.launch { runCatching { f.repository.fetchUser() } }
        runCurrent()
        f.repository.signOut()
        release.complete(Unit)
        call.join()

        assertEquals(SignedOutReason.UserSignedOut, assertIs<SessionState.SignedOut>(f.repository.state.value).reason)
        assertEquals(1, mal.userEndpointHits)
        f.repository.close()
    }

    @Test
    fun a_rejected_refresh_still_reports_refresh_rejected() = runTest {
        val mal = FakeMal(RefreshResponse.Rejected(HttpStatusCode.BadRequest, "invalid_grant"))
        val f = fixture(mal)
        f.repository.restore()
        runCurrent()

        runCatching { f.repository.fetchUser() }

        assertEquals(SignedOutReason.RefreshRejected, assertIs<SessionState.SignedOut>(f.repository.state.value).reason)
        f.repository.close()
    }
}

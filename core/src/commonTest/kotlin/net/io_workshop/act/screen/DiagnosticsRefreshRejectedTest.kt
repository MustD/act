package net.io_workshop.act.screen

import net.io_workshop.act.animelist.AnimeListLayout
import net.io_workshop.act.animelist.AnimeListState
import net.io_workshop.act.animepage.AnimePageHistory
import net.io_workshop.act.animepage.PatchLog
import net.io_workshop.act.auth.SignInState
import net.io_workshop.act.session.FakeClock
import net.io_workshop.act.session.FakeKeyValueStore
import net.io_workshop.act.session.FakeMal
import net.io_workshop.act.session.JsonTokenStore
import net.io_workshop.act.session.MalSessionRepository
import net.io_workshop.act.session.RefreshResponse
import net.io_workshop.act.session.SessionControls
import net.io_workshop.act.session.SignedOutReason
import net.io_workshop.act.session.TEST_CONFIG
import net.io_workshop.act.session.TEST_USER
import net.io_workshop.act.session.VALID_TOKENS
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Force 401, then Reload profile, with MAL rejecting the refresh — end to end through the real
 * repository, controls and [ScreenStateSource]: the Diagnostics Screen is left for the Welcome Screen,
 * and the Welcome Screen says why.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsRefreshRejectedTest {

    @Test
    fun force_401_then_reload_profile_with_the_refresh_rejected_lands_on_welcome_with_the_explanation() = runTest {
        val mal = FakeMal(RefreshResponse.Rejected(HttpStatusCode.BadRequest, errorCode = "invalid_grant"))
        val store = JsonTokenStore(FakeKeyValueStore(), clock = FakeClock())
        store.writeSession(VALID_TOKENS, TEST_USER)
        val repository = MalSessionRepository(
            store = store,
            clock = FakeClock(),
            initialConfig = TEST_CONFIG,
            clientFactory = mal.clientFactory,
        )
        repository.restore()
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val controls = SessionControls(repository, scope)
        val choice = ScreenChoice(repository.state, scope)
        val source = ScreenStateSource(
            session = repository.state,
            config = repository.config,
            animeList = MutableStateFlow(AnimeListState()),
            layout = MutableStateFlow(AnimeListLayout.Cards),
            animePages = MutableStateFlow(AnimePageHistory()),
            patchLog = MutableStateFlow(PatchLog()),
            signIn = MutableStateFlow(SignInState()),
            controls = controls.state,
            picked = choice.picked,
            scope = scope,
        )

        choice.openDiagnostics()
        assertEquals(SignedInScreen.Diagnostics, assertIs<ScreenState.SignedIn>(source.state.value).screen)

        controls.forceExpireAccessToken()
        controls.state.first { !it.busy }
        controls.refreshUser()
        val welcome = source.state.first { it is ScreenState.SignedOut } as ScreenState.SignedOut

        assertEquals(SignedOutScreen.Welcome, welcome.screen)
        assertEquals(explain(SignedOutReason.RefreshRejected), welcome.explanation)
        repository.close()
    }
}

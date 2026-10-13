package io.challenge_workshop.mal_ui.screen

import io.challenge_workshop.mal_ui.animelist.AnimeListLayout
import io.challenge_workshop.mal_ui.animelist.AnimeListState
import io.challenge_workshop.mal_ui.animepage.AnimePageHistory
import io.challenge_workshop.mal_ui.animepage.PatchLog
import io.challenge_workshop.mal_ui.auth.SignInState
import io.challenge_workshop.mal_ui.session.FakeClock
import io.challenge_workshop.mal_ui.session.FakeKeyValueStore
import io.challenge_workshop.mal_ui.session.FakeMal
import io.challenge_workshop.mal_ui.session.JsonTokenStore
import io.challenge_workshop.mal_ui.session.MalSessionRepository
import io.challenge_workshop.mal_ui.session.RefreshResponse
import io.challenge_workshop.mal_ui.session.SessionControls
import io.challenge_workshop.mal_ui.session.SignedOutReason
import io.challenge_workshop.mal_ui.session.TEST_CONFIG
import io.challenge_workshop.mal_ui.session.TEST_USER
import io.challenge_workshop.mal_ui.session.VALID_TOKENS
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

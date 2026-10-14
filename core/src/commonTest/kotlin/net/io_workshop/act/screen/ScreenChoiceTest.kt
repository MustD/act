package net.io_workshop.act.screen

import net.io_workshop.act.animelist.AnimeListLayout
import net.io_workshop.act.animelist.AnimeListState
import net.io_workshop.act.animepage.AnimePageHistory
import net.io_workshop.act.animepage.PatchLog
import net.io_workshop.act.auth.SignInState
import net.io_workshop.act.mal.MalAuthConfig
import net.io_workshop.act.session.SessionControlsState
import net.io_workshop.act.session.SessionState
import net.io_workshop.act.session.SignedOutReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Which Screen shows, as assertions over [ScreenState] on every Target: [ScreenChoice] feeding a real
 * [ScreenStateSource], with the Session as the only thing driven by hand.
 */
class ScreenChoiceTest {

    private val session = MutableStateFlow<SessionState>(SessionState.SignedOut(SignedOutReason.NeverSignedIn))
    private val animePages = MutableStateFlow(AnimePageHistory())
    private val animeList = MutableStateFlow(AnimeListState())
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val choice = ScreenChoice(session, scope)

    private val source = ScreenStateSource(
        session = session,
        config = MutableStateFlow(MalAuthConfig(clientId = "id", redirectUri = "app://redirect")),
        animeList = animeList,
        layout = MutableStateFlow(AnimeListLayout.Cards),
        animePages = animePages,
        patchLog = MutableStateFlow(PatchLog()),
        signIn = MutableStateFlow(SignInState()),
        controls = MutableStateFlow(SessionControlsState()),
        picked = choice.picked,
        scope = scope,
    )

    private fun signedOutScreen() = assertIs<ScreenState.SignedOut>(source.state.value).screen
    private fun signedInScreen() = assertIs<ScreenState.SignedIn>(source.state.value).screen
    private fun signIn() { session.value = SessionState.SignedIn(user = null) }

    @Test
    fun signed_out_opens_on_welcome_and_signed_in_on_the_anime_list() {
        assertEquals(SignedOutScreen.Welcome, signedOutScreen())
        signIn()
        assertEquals(SignedInScreen.AnimeList, signedInScreen())
    }

    @Test
    fun about_opens_signed_out_and_back_returns_to_welcome() {
        choice.openAbout()
        assertEquals(SignedOutScreen.About, signedOutScreen())
        choice.back()
        assertEquals(SignedOutScreen.Welcome, signedOutScreen())
    }

    @Test
    fun about_and_diagnostics_open_signed_in_and_back_returns_to_the_list() {
        signIn()
        choice.openAbout()
        assertEquals(SignedInScreen.About, signedInScreen())
        choice.openDiagnostics()
        assertEquals(SignedInScreen.Diagnostics, signedInScreen())
        choice.back()
        assertEquals(SignedInScreen.AnimeList, signedInScreen())
    }

    @Test
    fun opening_another_screen_replaces_the_one_showing_with_no_stack() {
        signIn()
        choice.openDiagnostics()
        choice.openAbout()
        assertEquals(SignedInScreen.About, signedInScreen())
        choice.back()
        assertEquals(SignedInScreen.AnimeList, signedInScreen(), "back is to the default, not to Diagnostics")
    }

    @Test
    fun diagnostics_cannot_be_opened_signed_out() {
        choice.openDiagnostics()
        assertEquals(SignedOutScreen.Welcome, signedOutScreen())
        // ...and it was not parked for later either.
        signIn()
        assertEquals(SignedInScreen.AnimeList, signedInScreen())
    }

    @Test
    fun nothing_opens_while_restoring_or_authorizing() {
        session.value = SessionState.Restoring
        choice.openAbout()
        assertEquals(null, choice.picked.value)
        session.value = SessionState.SignedOut(SignedOutReason.NeverSignedIn)
        assertEquals(SignedOutScreen.Welcome, signedOutScreen())
    }

    @Test
    fun a_change_of_session_state_returns_to_the_default_screen() {
        signIn()
        choice.openAbout()
        session.value = SessionState.SignedOut(SignedOutReason.RefreshRejected)
        assertEquals(SignedOutScreen.Welcome, signedOutScreen())

        choice.openAbout()
        signIn()
        assertEquals(SignedInScreen.AnimeList, signedInScreen())

        // And it stays gone when the same kind of state comes round again.
        session.value = SessionState.SignedOut(SignedOutReason.UserSignedOut)
        signIn()
        assertEquals(SignedInScreen.AnimeList, signedInScreen())
    }

    @Test
    fun a_token_refresh_does_not_close_the_screen() {
        signIn()
        choice.openDiagnostics()
        session.value = SessionState.SignedIn(user = null, refreshing = true)
        assertEquals(SignedInScreen.Diagnostics, signedInScreen())
    }

    @Test
    fun the_anime_list_and_page_history_are_untouched_by_any_screen() {
        signIn()
        val before = assertIs<ScreenState.SignedIn>(source.state.value)
        choice.openAbout()
        choice.openDiagnostics()
        val during = assertIs<ScreenState.SignedIn>(source.state.value)
        choice.back()
        val after = assertIs<ScreenState.SignedIn>(source.state.value)

        assertEquals(before.list, during.list)
        assertEquals(before.animePages, during.animePages)
        assertEquals(before, after)
    }

    @Test
    fun a_pick_made_in_another_kind_of_session_state_is_ignored_even_before_it_is_cleared() {
        // No ScreenChoice here: the pick is handed to the source stale, as it is in the window before
        // ScreenChoice's own collector has cleared it.
        val stale = MutableStateFlow<ScreenChoice.Pick?>(ScreenChoice.Pick(SessionKind.SignedOut, ScreenChoice.Choice.About))
        val bare = ScreenStateSource(
            session = MutableStateFlow(SessionState.SignedIn(user = null)),
            config = MutableStateFlow(MalAuthConfig(clientId = "id", redirectUri = "app://redirect")),
            animeList = animeList,
            layout = MutableStateFlow(AnimeListLayout.Cards),
            animePages = animePages,
            patchLog = MutableStateFlow(PatchLog()),
            signIn = MutableStateFlow(SignInState()),
            controls = MutableStateFlow(SessionControlsState()),
            picked = stale,
            scope = scope,
        )
        assertEquals(SignedInScreen.AnimeList, assertIs<ScreenState.SignedIn>(bare.state.value).screen)
    }
}

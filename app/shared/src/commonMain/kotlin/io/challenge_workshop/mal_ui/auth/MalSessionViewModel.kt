package io.challenge_workshop.mal_ui.auth

import androidx.lifecycle.ViewModel
import io.challenge_workshop.mal_ui.mal.MalAuthConfig
import io.challenge_workshop.mal_ui.session.MalSessionRepository
import io.challenge_workshop.mal_ui.session.SessionControlsState
import io.challenge_workshop.mal_ui.session.SessionControls
import io.challenge_workshop.mal_ui.session.SessionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Compose's adapter onto [MalSessionRepository], [SignIn] and [SessionControls].
 *
 * **On its way out.** The Sign-in has moved to [SignIn], a process-scoped module in `:core`, and the
 * sign-in methods here only forward to it. The signed-in screen's one-shot operations have moved to
 * [SessionControls] the same way.
 */
class MalSessionViewModel(
    private val repository: MalSessionRepository,
    private val signIn: SignIn,
    private val sessionControls: SessionControls,
) : ViewModel() {

    val state: StateFlow<SessionState> = repository.state

    /** The live config: the remembered Client ID only reaches it after `restore()`. */
    val config: StateFlow<MalAuthConfig> = repository.config

    val signInState: StateFlow<SignInState> = signIn.state

    val controls: StateFlow<SessionControlsState> = sessionControls.state

    fun onClientIdChange(value: String) = signIn.setClientId(value)

    fun onPastedRedirectChange(value: String) = signIn.setPastedRedirect(value)

    fun signIn(channel: AuthRedirectChannel, openUri: (String) -> Unit) = signIn.start(channel, openUri)

    fun completeSignIn() = signIn.completePasted()

    fun cancelSignIn() = signIn.cancel()

    fun signOut() = sessionControls.signOut()

    fun refreshUser() = sessionControls.refreshUser()

    fun reloadDiagnostics() = sessionControls.reloadDiagnostics()

    fun forceExpireAccessToken() = sessionControls.forceExpireAccessToken()
}

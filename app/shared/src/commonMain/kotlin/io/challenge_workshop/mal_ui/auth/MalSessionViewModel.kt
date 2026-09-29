package io.challenge_workshop.mal_ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.challenge_workshop.mal_ui.mal.MalAuthConfig
import io.challenge_workshop.mal_ui.session.MalSessionRepository
import io.challenge_workshop.mal_ui.session.SessionControlsState
import io.challenge_workshop.mal_ui.session.SessionOperation
import io.challenge_workshop.mal_ui.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Compose's adapter onto [MalSessionRepository] and [SignIn].
 *
 * **On its way out.** The Sign-in has moved to [SignIn], a process-scoped module in `:core`, and the
 * sign-in methods here only forward to it. What is left is the signed-in screen's one-shot
 * operations, which move to their own module next.
 */
class MalSessionViewModel(
    private val repository: MalSessionRepository,
    private val signIn: SignIn,
) : ViewModel() {

    val state: StateFlow<SessionState> = repository.state

    /** The live config: the remembered Client ID only reaches it after `restore()`. */
    val config: StateFlow<MalAuthConfig> = repository.config

    val signInState: StateFlow<SignInState> = signIn.state

    private val _controls = MutableStateFlow(SessionControlsState())
    val controls: StateFlow<SessionControlsState> = _controls.asStateFlow()

    fun onClientIdChange(value: String) = signIn.setClientId(value)

    fun onPastedRedirectChange(value: String) = signIn.setPastedRedirect(value)

    fun signIn(channel: AuthRedirectChannel, openUri: (String) -> Unit) = signIn.start(channel, openUri)

    fun completeSignIn() = signIn.completePasted()

    fun cancelSignIn() = signIn.cancel()

    fun signOut() = operation { repository.signOut() }

    fun refreshUser() = operation {
        repository.fetchUser()
        reloadDiagnosticsNow()
    }

    fun reloadDiagnostics() = operation { reloadDiagnosticsNow() }

    /** Debug panel: invalidate the access token so the next call refreshes against real MAL. */
    fun forceExpireAccessToken() = operation {
        repository.forceExpireAccessToken()
        reloadDiagnosticsNow()
    }

    private suspend fun reloadDiagnosticsNow() {
        val diagnostics = repository.diagnostics()
        _controls.update { it.copy(diagnostics = diagnostics) }
    }

    private fun operation(block: suspend () -> Unit) {
        _controls.update { it.copy(operation = SessionOperation.Running) }
        viewModelScope.launch {
            try {
                block()
                _controls.update { it.copy(operation = SessionOperation.Idle) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _controls.update { it.copy(operation = SessionOperation.Failed(e.message ?: e.toString())) }
            }
        }
    }
}

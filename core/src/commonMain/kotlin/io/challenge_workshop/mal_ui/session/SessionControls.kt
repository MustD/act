package io.challenge_workshop.mal_ui.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * The signed-in screen's one-shot operations — sign-out, reloading the profile, the debug panel's
 * buttons — with a status of their own.
 *
 * Separate from `SignIn` on purpose: a failed sign-out is not a
 * failed Sign-in and must not be shown as one. Nothing here references `SignIn`; the attempt ends
 * because `SignIn` sees the Session leave `Authorizing`.
 *
 * Process-scoped, like the Session it operates on: a `single` with a scope of its own.
 *
 * **One operation at a time.** A call made while another is [SessionOperation.Running] is dropped,
 * not queued. Otherwise the first job to finish would clear the second's spinner.
 *
 * @param scope where every operation runs. Production uses `Dispatchers.Main.immediate`.
 */
class SessionControls(
    private val repository: MalSessionRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SessionControlsState())
    val state: StateFlow<SessionControlsState> = _state.asStateFlow()

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
        _state.update { it.copy(diagnostics = diagnostics) }
    }

    private fun operation(block: suspend () -> Unit) {
        // Claimed atomically, so two callers on different threads cannot both see `Idle`.
        var claimed = false
        _state.update {
            claimed = !it.busy
            if (claimed) it.copy(operation = SessionOperation.Running) else it
        }
        if (!claimed) return
        scope.launch {
            val outcome = try {
                block()
                SessionOperation.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SessionOperation.Failed(e.message ?: e.toString())
            }
            _state.update { it.copy(operation = outcome) }
        }
    }
}

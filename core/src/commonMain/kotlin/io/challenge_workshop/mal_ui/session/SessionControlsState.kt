package io.challenge_workshop.mal_ui.session

/** Where the signed-in screen's one-shot operation is: sign-out, reload profile, the debug panel's buttons. */
sealed interface SessionOperation {
    data object Idle : SessionOperation
    data object Running : SessionOperation
    data class Failed(val message: String) : SessionOperation
}

/**
 * What the signed-in screen's operations have to show: token metadata for the debug panel, and how
 * the last operation went.
 *
 * Never token values: [SessionDiagnostics] has no field that could hold one.
 */
data class SessionControlsState(
    /** Null until the diagnostics dialog asks for it, which is the only thing that reads it. */
    val diagnostics: SessionDiagnostics? = null,
    val operation: SessionOperation = SessionOperation.Idle,
) {
    val busy: Boolean get() = operation is SessionOperation.Running
    val error: String? get() = (operation as? SessionOperation.Failed)?.message
}

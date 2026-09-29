package io.challenge_workshop.mal_ui.auth

/**
 * Where a [SignIn] is, as a value.
 *
 * One phase rather than a `busy` flag beside a comment, because "what is in flight" is the thing
 * every control on the sign-in and authorizing screens is gated on, and two booleans disagree about
 * it in ways a sealed type cannot.
 */
sealed interface SignInPhase {
    /** Nothing in flight. Also where a Sign-in rests after it ended without an outcome to report. */
    data object Idle : SignInPhase

    /** A Redirect Capture is being armed and the authorization minted. Nothing has left the app yet. */
    data object Arming : SignInPhase

    /**
     * The user is away on myanimelist.net and nothing is in flight — which is why Paste-the-code
     * stays available here: it is the way out when the capture never delivers.
     */
    data object AwaitingRedirect : SignInPhase

    /** A redirect, captured or pasted, is being exchanged for tokens. */
    data object Exchanging : SignInPhase

    /** The Sign-in ended in [message]. Cleared by the next start, paste or keystroke. */
    data class Failed(val message: String) : SignInPhase
}

/**
 * The ephemeral half of signing in: what is typed, and where the attempt is.
 *
 * Held by the process-scoped [SignIn], so a configuration change keeps it (ADR-0005). Losing it to
 * process death is correct — everything durable belongs to `MalSessionRepository`.
 */
data class SignInState(
    val clientId: String = "",
    val pastedRedirect: String = "",
    val phase: SignInPhase = SignInPhase.Idle,
) {
    /** Arming and exchanging are the two phases that must not be interrupted by a control. */
    val busy: Boolean get() = phase is SignInPhase.Arming || phase is SignInPhase.Exchanging

    /** The message of a [SignInPhase.Failed], for the screens that show it. */
    val error: String? get() = (phase as? SignInPhase.Failed)?.message

    /**
     * Signing in needs a Client ID and nothing else in flight. Allowed during
     * [SignInPhase.AwaitingRedirect]: starting again ends the first attempt.
     */
    val canStart: Boolean get() = clientId.isNotBlank() && !busy

    /** Paste-the-code needs something pasted and nothing else in flight. */
    val canComplete: Boolean get() = pastedRedirect.isNotBlank() && !busy
}

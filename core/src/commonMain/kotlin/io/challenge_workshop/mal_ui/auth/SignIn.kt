package io.challenge_workshop.mal_ui.auth

import io.challenge_workshop.mal_ui.session.MalSessionRepository
import io.challenge_workshop.mal_ui.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * The Sign-in: from arming a Redirect Capture, through the user's time away on myanimelist.net, to
 * the token exchange or to backing out.
 *
 * Process-scoped, like [MalSessionRepository] and the Anime List: a `single` with a scope of its own,
 * so nothing about a screen or composition going away ends an attempt. **What ends one is only an
 * explicit event** — its own outcome, [cancel], a new [start], or the Session leaving `Authorizing`
 * by a route this class did not take (a sign-out, a startup redirect).
 *
 * In `:core` so that the orchestration — arm, open, await, and the race between a capture and
 * Paste-the-code — is covered by `:core:allTests` on all four Targets. [AuthRedirectChannel] has no
 * Compose dependency, and this is the layer that was in a ViewModel only because nothing else was
 * there.
 *
 * **One redirect parser, one set of errors.** A captured redirect and a paste both go through
 * [MalSessionRepository.completeAuthorization]; `error=access_denied` reads the same either way.
 *
 * Construction restores the Session, seeds the Client ID from what that settled, and only then takes
 * the [StartupRedirect] — in that order in code, because `restore()` settles the state from the
 * store and a redirect completed before it would have its `SignedIn` overwritten a moment later.
 *
 * @param scope where every attempt runs. Production uses `Dispatchers.Main.immediate`, which is
 * load-bearing on web: see [start].
 */
class SignIn(
    private val repository: MalSessionRepository,
    private val startupRedirect: StartupRedirect,
    private val scope: CoroutineScope,
) {

    /**
     * The form, prefilled so the Client ID reads as an override rather than a mandatory step: the one
     * the device remembers, else the build-time `mal.clientId`. It is seeded twice, because a
     * remembered value comes out of the store and so is not in the config until `restore()` returns.
     *
     * There is deliberately no Client Secret field. `MalAuthConfig.clientSecret` stays, because it is
     * correct for a `web`-type app, but offering it in the UI only creates a way to mis-register.
     */
    private val _state = MutableStateFlow(SignInState(clientId = repository.config.value.clientId))
    val state: StateFlow<SignInState> = _state.asStateFlow()

    /** The attempt that arms, opens and awaits — alive for as long as the user is away. */
    private var attempt: Job? = null

    /** A paste's or a startup redirect's exchange. Apart from [attempt], which it may outlive or end. */
    private var exchange: Job? = null

    init {
        // Before `restore()`, so a transition it causes is seen by this.
        scope.launch { watchSession() }
        scope.launch {
            guarded {
                repository.restore()
                // Safe to overwrite: nothing can have been typed yet, the Restoring screen has no field.
                _state.update { it.copy(clientId = repository.config.value.clientId) }
                startupRedirect.consume()?.let { raw -> exchange = launchExchange(raw) }
            }
        }
    }

    fun setClientId(value: String) {
        _state.update { it.copy(clientId = value, phase = it.phase.unlessFailed()) }
        repository.useClientId(value)
    }

    fun setPastedRedirect(value: String) {
        _state.update { it.copy(pastedRedirect = value, phase = it.phase.unlessFailed()) }
    }

    /**
     * Starts a Sign-in: arms this target's Redirect Capture, mints the authorization URL, and sends the
     * user to MAL. A Sign-in already under way is ended first.
     *
     * [channel] is passed in rather than held, because it comes from a `@Composable`. [openUri] is
     * the browser-opening fallback for when the channel reports [ArmResult.Unsupported] and
     * Paste-the-code takes over; an armed channel opens the browser itself, since on web that call *is*
     * the popup.
     *
     * Nothing here logs the URL: under `plain` PKCE the code verifier travels inside it.
     *
     * **The stretch from the click to [AuthRedirectChannel.open] must not really suspend on web** — a
     * popup loses its user activation if it does, and WebKit's window is 1 second. So the attempt
     * launches [CoroutineStart.UNDISPATCHED]: it runs in the caller's frame up to its first real
     * suspension, whatever [scope]'s dispatcher is. That holds only while neither the web `arm` nor
     * [MalSessionRepository.beginAuthorization] suspends for real, which `PopupUserActivationTest`
     * pins against the production dispatcher. Desktop is the counter-example: its `arm` binds a socket
     * on `Dispatchers.IO` and genuinely dispatches.
     */
    fun start(channel: AuthRedirectChannel, openUri: (String) -> Unit) {
        if (!_state.value.canStart) return
        endAttempt()
        repository.useClientId(_state.value.clientId)
        _state.update { it.copy(phase = SignInPhase.Arming) }
        // One coroutine for the whole attempt, including the wait. An armed channel that is never
        // awaited can never be released, so nothing may come between arming it and awaiting it.
        attempt = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            guarded {
                when (val armed = channel.arm(repository.config.value.redirectUri)) {
                    // Reported before anything is minted and before the user has approved anything on
                    // MAL — which is the entire reason `arm` is a phase of its own.
                    is ArmResult.Failed -> fail(armed.message)

                    // No capture here, so the caller opens the browser itself and Paste-the-code takes
                    // over. Calling `open` on a channel that declined to arm would capture nothing.
                    ArmResult.Unsupported -> {
                        openUri(repository.beginAuthorization())
                        _state.update { it.copy(phase = SignInPhase.Idle) }
                    }

                    ArmResult.Armed -> {
                        channel.open(repository.beginAuthorization())
                        _state.update { it.copy(phase = SignInPhase.AwaitingRedirect) }
                        awaitCapture(channel)
                    }
                }
            }
        }
    }

    /** Every outcome lands on a path the paste field already uses, rather than a parallel one. */
    private suspend fun awaitCapture(channel: AuthRedirectChannel) {
        when (val captured = channel.await()) {
            is AuthRedirectResult.Received ->
                // A paste is already being exchanged, and a code is single-use: one exchange only.
                if (_state.value.phase !is SignInPhase.Exchanging) exchangeRedirect(captured.rawRedirect)

            AuthRedirectResult.Cancelled -> {
                repository.cancelAuthorization()
                idleUnlessFailed()
            }

            // The capture broke, not the authorization: the Session stays `Authorizing`, where the URL
            // and the paste field are both still on screen.
            is AuthRedirectResult.Failed -> fail(captured.message)

            // Nothing went wrong — a listener that timed out. Paste-the-code, and no apology.
            AuthRedirectResult.Unsupported -> idleUnlessFailed()
        }
    }

    /**
     * Paste-the-code. A paste that beats the capture ends the attempt's await, which is how the
     * channel is released — but only once the exchange has worked: a paste that fails leaves the
     * capture listening.
     */
    fun completePasted() {
        val current = _state.value
        if (!current.canComplete) return
        exchange?.cancel()
        exchange = launchExchange(current.pastedRedirect, releaseCapture = true)
    }

    /** Backing out. Keeps the Pending Authorization — a redirect that lands later is still good. */
    fun cancel() {
        endAttempt()
        _state.update { it.copy(phase = SignInPhase.Idle) }
        scope.launch { guarded { repository.cancelAuthorization() } }
    }

    private fun launchExchange(rawRedirect: String, releaseCapture: Boolean = false): Job = scope.launch {
        guarded { exchangeRedirect(rawRedirect, releaseCapture) }
    }

    private suspend fun exchangeRedirect(rawRedirect: String, releaseCapture: Boolean = false) {
        _state.update { it.copy(phase = SignInPhase.Exchanging) }
        try {
            repository.completeAuthorization(rawRedirect)
        } finally {
            // Before the phase leaves `Exchanging`, so nothing observing the phase can see a finished
            // Sign-in whose capture is still listening. Also on the profile fetch failing behind a
            // good exchange: the Session exists, so there is nothing left to capture.
            if (releaseCapture && repository.state.value is SessionState.SignedIn) attempt?.cancel()
        }
        _state.update { it.copy(pastedRedirect = "", phase = SignInPhase.Idle) }
    }

    /**
     * Runs [block], turning what it throws into [SignInPhase.Failed]. Cancellation is not a failure:
     * cancelling a Sign-in is routine and reporting it would put "job was cancelled" in an error card.
     */
    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e.message ?: e.toString())
        }
    }

    /** A capture that ends without an outcome must not erase a failure the user has not yet seen. */
    private fun idleUnlessFailed() {
        _state.update { if (it.phase is SignInPhase.Failed) it else it.copy(phase = SignInPhase.Idle) }
    }

    private fun fail(message: String) {
        // A failure after the Session is already `SignedIn` — the profile fetch that follows a
        // successful exchange — is not a failed *Sign-in*, and left as one it would resurface on the
        // sign-in screen after the next sign-out.
        val phase = if (repository.state.value is SessionState.SignedIn) {
            SignInPhase.Idle
        } else {
            SignInPhase.Failed(message)
        }
        _state.update { it.copy(phase = phase) }
    }

    /**
     * Ends the attempt when the Session leaves `Authorizing` by a route this class did not take.
     *
     * Ignored while [SignInPhase.Exchanging], which is this class taking the route itself: cancelling
     * then would abandon a token exchange, or the profile fetch behind it, halfway.
     */
    private suspend fun watchSession() {
        var previous = repository.state.value
        repository.state.collect { now ->
            val leftAuthorizing = previous is SessionState.Authorizing && now !is SessionState.Authorizing
            previous = now
            if (leftAuthorizing && _state.value.phase !is SignInPhase.Exchanging) {
                endAttempt()
                // A Failed phase is this class's own outcome and outlives the transition that caused it.
                _state.update {
                    it.copy(
                        pastedRedirect = "",
                        phase = if (it.phase is SignInPhase.Failed) it.phase else SignInPhase.Idle,
                    )
                }
            }
        }
    }

    /** Cancelling is a Redirect Capture's only teardown path: it releases a bound port or a popup. */
    private fun endAttempt() {
        attempt?.cancel()
        exchange?.cancel()
    }

    /** Edits clear a failure, and only a failure. */
    private fun SignInPhase.unlessFailed(): SignInPhase = if (this is SignInPhase.Failed) SignInPhase.Idle else this
}

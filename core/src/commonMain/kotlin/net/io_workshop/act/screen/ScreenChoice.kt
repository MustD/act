package net.io_workshop.act.screen

import net.io_workshop.act.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The Screens a signed-out user can be on. [Welcome] is the default. */
enum class SignedOutScreen { Welcome, About }

/** The Screens a signed-in user can be on. [AnimeList] is the default. */
enum class SignedInScreen { AnimeList, About, Diagnostics }

/**
 * Which Screen the user has opened, if not the default — one input to [ScreenStateSource].
 *
 * One at a time and no stack: opening another replaces the one showing, and [back] returns to the
 * default. Not persisted, so a restart opens on the default. Nothing here touches the Anime List, its
 * pager or the Anime Page history: they are other inputs, and this is only a choice among Screens.
 *
 * **A pick belongs to the kind of Session State it was made in** ([SessionKind]), which is how "any
 * change of Session State returns to the default" holds without a race. [picked] is cleared when the
 * kind changes, but that happens in a collector of its own, and a [ScreenStateSource] combine can see
 * the new Session before the clear lands; it therefore also ignores a pick whose kind is not the
 * current one. "Change" means a change of kind — a token refresh re-emits `SignedIn` with the same
 * kind and must not throw the user off the About Screen.
 *
 * Opening a Screen the current Session State does not have — Diagnostics while signed out, anything
 * while Restoring or Authorizing — does nothing.
 *
 * @param scope process-scoped, like the Session it watches.
 */
class ScreenChoice(
    private val session: StateFlow<SessionState>,
    scope: CoroutineScope,
) {
    private val _picked = MutableStateFlow<Pick?>(null)

    /** The open Screen, or null for the default. */
    val picked: StateFlow<Pick?> = _picked.asStateFlow()

    init {
        scope.launch {
            // `drop(1)`: the first emission is the state this was built in, not a change, and must not
            // wipe a pick made before the collector first ran.
            session.map { it.kind() }.distinctUntilChanged().drop(1).collect { _picked.value = null }
        }
    }

    fun openAbout() {
        val kind = session.value.kind()
        if (kind == SessionKind.SignedOut || kind == SessionKind.SignedIn) {
            _picked.value = Pick(kind, Choice.About)
        }
    }

    fun openDiagnostics() {
        val kind = session.value.kind()
        if (kind == SessionKind.SignedIn) _picked.value = Pick(kind, Choice.Diagnostics)
    }

    /** Back to the default Screen. */
    fun back() {
        _picked.value = null
    }

    enum class Choice { About, Diagnostics }

    /** [screen] opened while the Session State was a [kind]. */
    data class Pick(val kind: SessionKind, val screen: Choice)
}

/** Which [SessionState] subtype, without its fields. */
enum class SessionKind { Restoring, SignedOut, Authorizing, SignedIn }

internal fun SessionState.kind(): SessionKind = when (this) {
    SessionState.Restoring -> SessionKind.Restoring
    is SessionState.SignedOut -> SessionKind.SignedOut
    is SessionState.Authorizing -> SessionKind.Authorizing
    is SessionState.SignedIn -> SessionKind.SignedIn
}

package io.challenge_workshop.mal_ui.screen

import io.challenge_workshop.mal_ui.animelist.AnimeListLayout
import io.challenge_workshop.mal_ui.animelist.AnimeListState
import io.challenge_workshop.mal_ui.auth.SignInState
import io.challenge_workshop.mal_ui.mal.MalEndpoints
import io.challenge_workshop.mal_ui.mal.MalUser
import io.challenge_workshop.mal_ui.session.SessionDiagnostics
import io.challenge_workshop.mal_ui.session.SessionState
import io.challenge_workshop.mal_ui.session.SignedOutReason

/**
 * What one of the app's four destinations needs in order to draw itself: a Session and an Anime List,
 * resolved into a value.
 *
 * **Not a synonym for [SessionState].** That is the *Session's* own state — whether there are tokens
 * and what happened to them — and it is one of this value's six inputs. This is the whole screen,
 * including a list the Session has never heard of and a Layout that reaches MAL never. The two are as
 * easy to confuse as Watch Status and Airing Status, and for the same reason: one variant per
 * `SessionState` subtype makes them look like the same thing renamed.
 *
 * **Data only — no lambdas.** Compose skips a composable when its arguments are `equals`, and a
 * `data class` holding a `() -> Unit` is neither equal across recompositions nor stable, so folding
 * the callbacks in would recompose the whole signed-in screen — the grid included — on every
 * emission. They live in separate per-screen actions records in `:app:shared` instead.
 *
 * In `:core` and not in `:app:shared` because the *choice* is `:core`'s and the *drawing* is
 * `:app:shared`'s, exactly as for [AnimeListLayout] and `AnimeListSortOrder` — and because
 * `./gradlew :core:allTests` is the one task that runs a test on four Targets in one command. See
 * `docs/adr/0004-screen-state-in-core.md`.
 */
sealed interface ScreenState {

    /** App start: the store is being read. A splash, deliberately not a sign-in form. */
    data object Restoring : ScreenState

    /**
     * No Session. The sign-in screen, which has no password field and never will: MAL supports only
     * the authorization code grant.
     *
     * [explanation] rather than the [SignedOutReason] it came from, because the whole point of that
     * enum is the *copy* — an expired Session must not read like a deliberate sign-out — and a
     * variant carrying the enum would let a screen be asserted without ever comparing what it says.
     * That is display copy in `:core`, which the Sort Order labels deliberately are not; the
     * difference is that this text is the state, and the tests that hold the four apart are the
     * reason the state exists.
     */
    data class SignedOut(
        val explanation: String,
        /** What went wrong on the way out, if the Session ended in a failure rather than a choice. */
        val error: String?,
        val signIn: SignInState,
        val routing: MalRouting,
        /** See [MalRouting.suggestsDeadRelay]; true when [error] or the Sign-in's failure reads that way. */
        val relayHint: Boolean = false,
    ) : ScreenState

    /**
     * The user is away on myanimelist.net.
     *
     * [authorizationUrl] is a **field**, rebuilt in the mapping from the config and the Pending
     * Authorization, because Paste-the-code has to be able to offer it after a restart. Under `plain`
     * PKCE the code verifier is inside that string: assertable in a test, and never to be logged.
     */
    data class Authorizing(
        val authorizationUrl: String,
        val signIn: SignInState,
        /** See [MalRouting.suggestsDeadRelay]; true when the Sign-in's failure reads that way. */
        val relayHint: Boolean = false,
    ) : ScreenState

    /**
     * There is a Session, so the screen **is** the Anime List.
     *
     * [list] nests [AnimeListState] verbatim rather than restating its fields. That value already
     * says which of its screens the list is, and a second copy of that decision in a second file
     * drifts on the next screen added.
     *
     * [refreshing] is the *Session* refreshing, not the list — it draws as a spinner beside the name
     * precisely because a refresh must not unmount this screen.
     *
     * [busy] and [error] are the signed-in operation's, flattened: a sign-out can be in flight and a
     * failure has to land somewhere the user is looking.
     */
    data class SignedIn(
        val user: MalUser?,
        val refreshing: Boolean,
        val list: AnimeListState,
        val layout: AnimeListLayout,
        val busy: Boolean,
        val error: String?,
        /** Null until the diagnostics dialog asks for it, which is the only thing that reads it. */
        val diagnostics: SessionDiagnostics?,
        val routing: MalRouting,
        /** See [MalRouting.suggestsDeadRelay]; true when [error] reads that way. */
        val relayHint: Boolean = false,
    ) : ScreenState
}

/**
 * Where this build actually sends MAL traffic, which is otherwise unanswerable from inside a running
 * app: a misrouted web build talks to the wrong host silently, and a Redirect URI mismatch reports as
 * a 401 about the Client ID.
 *
 * On two variants rather than one: the sign-in screen says so up front because a browser with no
 * relay running fails at the first request, and the debug panel says so afterwards.
 */
data class MalRouting(
    val endpoints: MalEndpoints,
    val redirectUri: String,
) {
    /** True on web, where token and API calls go through `:server` instead of straight to MAL. */
    val usesRelay: Boolean
        get() = !endpoints.tokenEndpoint.startsWith(MYANIMELIST_ORIGIN)

    /**
     * Whether [error] is probably a dead relay rather than a MAL answer. On web a dead relay surfaces
     * as a bare "Failed to fetch" with no status, because the browser blocks the request before it is
     * sent, so the likely cause is worth naming next to it. The *wording* is `:app:shared`'s.
     */
    fun suggestsDeadRelay(error: String?): Boolean {
        if (!usesRelay || error == null) return false
        val lower = error.lowercase()
        return "fetch" in lower || "could not reach" in lower
    }

    private companion object {
        const val MYANIMELIST_ORIGIN = "https://myanimelist.net"
    }
}

/**
 * Why a Session is absent, in words — the whole reason [SignedOutReason] is carried at all.
 *
 * Here rather than in a composable so that "these four say different things" is a four-Target
 * assertion over a value instead of four renderings on jvm.
 */
fun explain(reason: SignedOutReason): String = when (reason) {
    SignedOutReason.NeverSignedIn ->
        "MAL has no password grant, so nothing is typed here — you approve access on " +
            "myanimelist.net and come straight back."

    SignedOutReason.UserSignedOut ->
        "Signed out. Your tokens have been deleted from this device."

    SignedOutReason.RefreshRejected ->
        "Your MyAnimeList session expired and could not be renewed, so you will need to " +
            "approve access again."

    SignedOutReason.AuthorizationFailed ->
        "That sign-in attempt did not complete. Starting again mints a fresh code."
}

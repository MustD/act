package io.challenge_workshop.mal_ui.screen

import io.challenge_workshop.mal_ui.animelist.AnimeListLayout
import io.challenge_workshop.mal_ui.animelist.AnimeListState
import io.challenge_workshop.mal_ui.animepage.AnimePageHistory
import io.challenge_workshop.mal_ui.animepage.SaveLog
import io.challenge_workshop.mal_ui.auth.SignInState
import io.challenge_workshop.mal_ui.mal.MalAuthConfig
import io.challenge_workshop.mal_ui.mal.MalEndpoints
import io.challenge_workshop.mal_ui.mal.platformMalEndpoints
import io.challenge_workshop.mal_ui.session.SessionControlsState
import io.challenge_workshop.mal_ui.session.SessionState
import io.challenge_workshop.mal_ui.session.authorizationUrlFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The one place a [ScreenState] is decided: eight flows in, one flow out.
 *
 * No Compose and no Koin, so `./gradlew :core:allTests` covers the mapping on every Target and
 * the alternative — the same `when` written inside a composable — cannot be tested at all without a
 * rendering harness per Target.
 *
 * **Eight inputs is the known cost.** The *caller* sees one flow; this constructor is wide. It is
 * defensible because each input has exactly one owner and the combine is total, but it is the thing a
 * review will push on, and the answer is not "it is fine" — it is that the alternative puts the
 * mapping back in a composable. See `docs/adr/0004-screen-state-in-core.md`.
 *
 * Four of those inputs are deliberately *not* the objects that own them:
 *  - `animeList` is `AnimeListRepository.state` rather than the repository, which also holds the
 *    operations — and those are the actions records' business, not a value's. Loaded pages not
 *    outliving a Session is the repository's rule, not this source's.
 *  - `layout` is `LayoutPreference.value`, and a Layout change issues no request, so the list has
 *    never heard of it.
 *  - `animePages` is `AnimePageRepository.state`, and only the signed-in variant carries it: the
 *    history ends with the Session, so no other variant has anything to show.
 *
 *  - `saveLog` is `AnimePageRepository.log`: the last PATCH sent and whether any save is pending. Not in
 *    `animePages` because it outlives the pages — a save runs on after its page is closed.
 *
 * The eight flows are the whole constructor. Where this build sends MAL traffic is *not* an eighth
 * parameter: [platformMalEndpoints] is an `expect fun` and therefore already this Target's answer, and
 * injecting it would be a seam with one production implementation — the same objection that kept a
 * `(PendingAuthorization) -> String` adapter out. A test asserts it against `platformMalEndpoints()`
 * on whichever Target it is running on, which is a stronger claim than a pinned value.
 *
 * @param scope where the combine runs. `SharingStarted.Eagerly`, because [state] is read by a
 * composable that must have an answer before it first draws — and [state]`.value` is right even
 * before that scope has dispatched anything, since the initial value is the same mapping applied to
 * the eight current values.
 */
class ScreenStateSource(
    session: StateFlow<SessionState>,
    config: StateFlow<MalAuthConfig>,
    animeList: StateFlow<AnimeListState>,
    layout: StateFlow<AnimeListLayout>,
    animePages: StateFlow<AnimePageHistory>,
    saveLog: StateFlow<SaveLog>,
    signIn: StateFlow<SignInState>,
    controls: StateFlow<SessionControlsState>,
    scope: CoroutineScope,
) {
    private val endpoints: MalEndpoints = platformMalEndpoints()

    val state: StateFlow<ScreenState> =
        // Eight flows through the *five*-argument overload, with the four that are not the Session's own
        // grouped first. `combine` is only typed up to five: the eight-argument form hands the lambda an
        // `Array<Any?>` to index and cast, which compiles just as happily when two inputs of the
        // same type are swapped. This keeps every input checked by the compiler.
        combine(
            session,
            config,
            animeList,
            layout,
            combine(signIn, controls, animePages, saveLog) { signIn, controls, animePages, saveLog ->
                Inputs(signIn, controls, animePages, saveLog)
            },
        ) { session, config, animeList, layout, (signIn, controls, animePages, saveLog) ->
            screenState(session, config, animeList, layout, animePages, saveLog, signIn, controls)
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = screenState(
                session = session.value,
                config = config.value,
                animeList = animeList.value,
                layout = layout.value,
                animePages = animePages.value,
                saveLog = saveLog.value,
                signIn = signIn.value,
                controls = controls.value,
            ),
        )

    /**
     * The four inputs that are not the Session's own, destructured by the combine above. A class and
     * not a `Quad`, which Kotlin does not have.
     */
    private data class Inputs(
        val signIn: SignInState,
        val controls: SessionControlsState,
        val animePages: AnimePageHistory,
        val saveLog: SaveLog,
    )

    /**
     * The mapping itself, total over [SessionState] by the compiler's own exhaustiveness check.
     *
     * Nothing here can throw. A combine that threw would take the collecting scope with it and leave
     * the app on whichever frame it last drew, which is the failure mode a screen-state seam exists to
     * make impossible — so the authorization URL is rebuilt through
     * [io.challenge_workshop.mal_ui.session.authorizationUrlFor], which has no `require` in it, rather
     * than through the minting path that does.
     */
    private fun screenState(
        session: SessionState,
        config: MalAuthConfig,
        animeList: AnimeListState,
        layout: AnimeListLayout,
        animePages: AnimePageHistory,
        saveLog: SaveLog,
        signIn: SignInState,
        controls: SessionControlsState,
    ): ScreenState {
        val routing = MalRouting(endpoints = endpoints, redirectUri = config.redirectUri)
        return when (session) {
            SessionState.Restoring -> ScreenState.Restoring

            is SessionState.SignedOut -> ScreenState.SignedOut(
                explanation = explain(session.reason),
                error = routing.shown(session.error),
                signIn = signIn,
                routing = routing,
                signInError = routing.shown(signIn.error),
            )

            is SessionState.Authorizing -> ScreenState.Authorizing(
                authorizationUrl = authorizationUrlFor(config, session.pending),
                signIn = signIn,
                signInError = routing.shown(signIn.error),
            )

            is SessionState.SignedIn -> ScreenState.SignedIn(
                user = session.user,
                refreshing = session.refreshing,
                list = animeList,
                layout = layout,
                animePages = animePages,
                saveLog = saveLog,
                busy = controls.busy,
                error = routing.shown(controls.error),
                diagnostics = controls.diagnostics,
                routing = routing,
            )
        }
    }
}

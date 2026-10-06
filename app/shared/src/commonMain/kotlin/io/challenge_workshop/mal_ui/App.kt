package io.challenge_workshop.mal_ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.setSingletonImageLoaderFactory
import io.challenge_workshop.mal_ui.animelist.AnimeListRepository
import io.challenge_workshop.mal_ui.animelist.LayoutPreference
import io.challenge_workshop.mal_ui.animelist.malImageLoader
import io.challenge_workshop.mal_ui.animepage.AnimePageRepository
import io.challenge_workshop.mal_ui.auth.AuthorizingScreen
import io.challenge_workshop.mal_ui.auth.SignIn
import io.challenge_workshop.mal_ui.auth.RestoringScreen
import io.challenge_workshop.mal_ui.auth.ScreenActions
import io.challenge_workshop.mal_ui.auth.SessionScreenTag
import io.challenge_workshop.mal_ui.auth.WelcomeScreen
import io.challenge_workshop.mal_ui.auth.SignedInScreen
import io.challenge_workshop.mal_ui.auth.rememberAuthRedirectChannel
import io.challenge_workshop.mal_ui.auth.screenActions
import io.challenge_workshop.mal_ui.screen.ScreenState
import io.challenge_workshop.mal_ui.screen.ScreenStateSource
import io.challenge_workshop.mal_ui.session.MalSessionRepository
import io.challenge_workshop.mal_ui.session.SessionControls
import org.koin.compose.koinInject

/**
 * The whole app: a `when` over [ScreenState].
 *
 * No navigation library. A four-destination switch does not need one, and adding one alongside Koin
 * would have meant two unfamiliar failure modes at once.
 *
 * There is no `@Preview` any more: the screen resolves its modules from Koin, so a preview without a
 * started Koin would fail at render time rather than usefully show anything.
 *
 * Resolving [SignIn] is also what restores the Session: its construction runs `restore()`.
 */
@Composable
fun App(
    repository: MalSessionRepository = koinInject(),
    signIn: SignIn = koinInject(),
    controls: SessionControls = koinInject(),
    animeList: AnimeListRepository = koinInject(),
    animePages: AnimePageRepository = koinInject(),
    layout: LayoutPreference = koinInject(),
) {
    // Coil's singleton, replaced here at the root because its default cannot fetch over the network
    // on the web Target — see [malImageLoader]. `setSingletonImageLoaderFactory` remembers the
    // factory, so this is once per process and not once per recomposition. Deliberately *not* in
    // `initKoin()`: the loader needs a `PlatformContext`, which on Android is the one thing only a
    // composition (or an Activity) has, and each entry point starting Koin differently is exactly
    // how two of the three Targets would end up without one.
    setSingletonImageLoaderFactory { context -> malImageLoader(context) }

    MaterialTheme {
        Surface(modifier = Modifier) {
            AppScreen(repository, signIn, controls, animeList, animePages, layout)
        }
    }
}

/**
 * The adapter between the Session, the Sign-in, the Session Controls, the Anime List, the Anime Pages, the Layout and
 * the one value the screens take.
 *
 * Split from [App] so it can be rendered without replacing Coil's singleton or re-theming, and split
 * from [SessionRoute] because *this* is the half that touches the modules: the routing below is a
 * `when` over a value and a record of lambdas, and nothing in it knows they exist.
 *
 * **Everything here is `remember`ed on what it was built from.** A [ScreenStateSource] rebuilt per
 * recomposition would restart its combine on every frame, and an actions record rebuilt per
 * recomposition would be a fresh object with fresh method references — an unstable argument, which
 * would stop Compose skipping and recompose the signed-in screen, grid included, on every emission.
 * That is the whole reason the callbacks are not fields of [ScreenState].
 *
 * **The [io.challenge_workshop.mal_ui.auth.AuthRedirectChannel] is bound here**, above the routing
 * `when` and outside every screen. It has to be: an Android `ActivityResultLauncher` can only be
 * registered from composition and androidx requires that registration to be unconditional and before
 * `STARTED`, and a channel that left composition when the sign-in screen swapped for the authorizing
 * one would take that launcher with it. Binding it here rather than inside the `when` is also what
 * lets `onSignIn` be a bare `() -> Unit`, so the channel never appears in a screen's interface.
 */
@Composable
internal fun AppScreen(
    repository: MalSessionRepository,
    signIn: SignIn,
    controls: SessionControls,
    animeList: AnimeListRepository,
    animePages: AnimePageRepository,
    layout: LayoutPreference,
) {
    val channel = rememberAuthRedirectChannel()
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    val source = remember(repository, signIn, controls, animeList, animePages, layout, scope) {
        ScreenStateSource(
            session = repository.state,
            config = repository.config,
            animeList = animeList.state,
            layout = layout.value,
            animePages = animePages.state,
            signIn = signIn.state,
            controls = controls.state,
            scope = scope,
        )
    }

    val actions = remember(signIn, controls, animeList, animePages, layout, channel, uriHandler) {
        screenActions(signIn, controls, animeList, animePages, layout, channel, uriHandler::openUri)
    }

    SessionRoute(source.state.collectAsStateWithLifecycle().value, actions)
}

/**
 * The routing itself: one [ScreenState] variant to one screen, and nothing else.
 *
 * The `when` is exhaustive over a sealed interface, so a new state cannot be added without the
 * compiler pointing here — which is what stops a state falling through to a blank screen. The
 * compiler only checks that each branch *exists*, though, so each one is tagged and `SessionRouteTest`
 * checks each one also renders something.
 *
 * No `modifier` parameter: both call sites are in this file and neither has anything to pass, and one
 * that existed only to be overwritten by `testTag` below would be a lie about the seam.
 *
 * Takes a value and a record of lambdas, which is what makes it renderable from a literal: no
 * repository, no store, no HTTP engine and no bound port. That is the whole point of the seam.
 */
@Composable
internal fun SessionRoute(state: ScreenState, actions: ScreenActions) {
    when (state) {
        ScreenState.Restoring ->
            RestoringScreen(Modifier.testTag(SessionScreenTag.Restoring.tag))

        is ScreenState.SignedOut ->
            WelcomeScreen(state, actions.signIn, Modifier.testTag(SessionScreenTag.SignIn.tag))

        is ScreenState.Authorizing ->
            AuthorizingScreen(state, actions.authorizing, Modifier.testTag(SessionScreenTag.Authorizing.tag))

        is ScreenState.SignedIn ->
            SignedInScreen(state, actions.signedIn, Modifier.testTag(SessionScreenTag.SignedIn.tag))
    }
}

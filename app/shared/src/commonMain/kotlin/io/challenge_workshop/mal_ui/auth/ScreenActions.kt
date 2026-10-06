package io.challenge_workshop.mal_ui.auth

import androidx.compose.runtime.Immutable
import io.challenge_workshop.mal_ui.animelist.AnimeListEntry
import io.challenge_workshop.mal_ui.animelist.AnimeListLayout
import io.challenge_workshop.mal_ui.animelist.AnimeListRepository
import io.challenge_workshop.mal_ui.animelist.AnimeListSortOrder
import io.challenge_workshop.mal_ui.animelist.LayoutPreference
import io.challenge_workshop.mal_ui.animelist.WatchStatus
import io.challenge_workshop.mal_ui.animepage.ListEdit
import io.challenge_workshop.mal_ui.animepage.AnimePageRepository
import io.challenge_workshop.mal_ui.animepage.RelatedAnime
import io.challenge_workshop.mal_ui.screen.ScreenState
import io.challenge_workshop.mal_ui.session.SessionControls

/**
 * Everything a screen can *do*, held apart from [ScreenState], which is everything a screen can *be*.
 *
 * **The split is not tidiness, it is how Compose skips.** A composable is skipped when its arguments
 * are `equals` and stable, and a `data class` holding `() -> Unit` fields is neither: two lambdas
 * built in the same place on two recompositions are different objects. Folding these into
 * `ScreenState` would therefore make every emission — a page landing, a spinner starting — recompose
 * the whole signed-in screen, grid included, which is the cost the value was introduced to avoid.
 *
 * They are [Immutable] and built **once**, with `remember`, in `App()`. Rebuilt per recomposition
 * they would be exactly the unstable argument described above, and the annotation would be a lie.
 *
 * Not a property of any one module: [SignedInActions] spans it, the Anime List and the Layout, and a
 * record any one of them exposed would have to reach into the others.
 */
@Immutable
data class ScreenActions(
    val signIn: SignInActions,
    val authorizing: AuthorizingActions,
    val signedIn: SignedInActions,
)

/**
 * The sign-in screen.
 *
 * [onSignIn] takes no arguments: the [AuthRedirectChannel] is bound where the modules are, above
 * the routing `when`, so the channel never appears in a screen's interface. It has to be bound there
 * anyway — an Android `ActivityResultLauncher` can only be registered from composition, before the
 * Activity reaches `STARTED`, and a channel that left composition when this screen swapped for the
 * authorizing one would take that launcher with it.
 */
@Immutable
data class SignInActions(
    val onSignIn: () -> Unit,
)

/** The authorizing screen: Paste-the-code, and backing out. */
@Immutable
data class AuthorizingActions(
    val onPastedRedirectChange: (String) -> Unit,
    val onCompleteSignIn: () -> Unit,
    val onCancelSignIn: () -> Unit,
)

/**
 * The signed-in screen, which **is** the Anime List.
 *
 * This record is the reason no module exposes an actions record of its own: Sign out and the
 * diagnostics dialog come from the Session, Reload and the two query controls from the Anime List,
 * and the Layout from its preference.
 *
 * There is no "load the first page": the Anime List asks for it itself as the Session starts.
 */
@Immutable
data class SignedInActions(
    val onLoadMore: () -> Unit,
    val onRetry: () -> Unit,
    val onReload: () -> Unit,
    val onSelectWatchStatus: (WatchStatus?) -> Unit,
    val onSelectSortOrder: (AnimeListSortOrder) -> Unit,
    val onSelectLayout: (AnimeListLayout) -> Unit,
    val onSignOut: () -> Unit,
    /** Tapping a List Entry: opens its Anime Page as a new history. */
    val onOpenAnime: (AnimeListEntry) -> Unit,
    val animePage: AnimePageActions,
    val diagnostics: DiagnosticsActions,
)

/**
 * The Anime Page, which is the signed-in screen's other half: [onBack] goes one page back in the
 * history (the panel's ←, the Android back gesture, desktop Esc), [onClose] closes all of it (✕).
 */
@Immutable
data class AnimePageActions(
    val onBack: () -> Unit,
    val onClose: () -> Unit,
    val onRetry: () -> Unit,
    /** A change to the List Entry on the current page. Saved as soon as it is made. */
    val onEdit: (ListEdit) -> Unit,
    /** "Add to list as…" on an anime that is not on the list: the chosen Watch Status. */
    val onAdd: (WatchStatus) -> Unit,
    /** Tapping a Related Anime: adds its page to the history. */
    val onOpenRelated: (RelatedAnime) -> Unit,
)

/**
 * The three buttons in the session debug panel.
 *
 * Its own record rather than three more fields on [SignedInActions], because the panel is behind a
 * dialog that only opens deliberately — and because "Force 401" and "Reload profile" being one row
 * apart is the whole procedure for watching a refresh happen against real MAL.
 */
@Immutable
data class DiagnosticsActions(
    val onReloadDiagnostics: () -> Unit,
    val onRefreshUser: () -> Unit,
    val onForceExpireAccessToken: () -> Unit,
)

/**
 * Wires the Sign-in, the Session Controls, the Anime List, the Anime Pages and the Layout to the three actions
 * records, in one place so `App()` and the rendering tests cannot drift about what a control does.
 *
 * Not a `@Composable` and not remembered here: the caller is what has to `remember` the result, since
 * the whole value of these records is being the *same object* across recompositions.
 *
 * @param openUri the browser-opening fallback for a target whose Redirect Capture declines to arm.
 * An armed [channel] opens the browser itself — on web that call *is* the popup.
 */
internal fun screenActions(
    signIn: SignIn,
    controls: SessionControls,
    animeList: AnimeListRepository,
    animePages: AnimePageRepository,
    layout: LayoutPreference,
    channel: AuthRedirectChannel,
    openUri: (String) -> Unit,
): ScreenActions = ScreenActions(
    signIn = SignInActions(
        // Straight through, with no `launch` between the click and the channel: a web popup's user
        // activation is a timestamp window and WebKit's is one second wide.
        onSignIn = { signIn.start(channel, openUri) },
    ),
    authorizing = AuthorizingActions(
        onPastedRedirectChange = signIn::setPastedRedirect,
        onCompleteSignIn = signIn::completePasted,
        onCancelSignIn = signIn::cancel,
    ),
    signedIn = SignedInActions(
        onLoadMore = animeList::loadMore,
        onRetry = animeList::retry,
        onReload = animeList::reload,
        onSelectWatchStatus = animeList::setWatchStatus,
        onSelectSortOrder = animeList::setSortOrder,
        // Straight to the preference, which switches inside the click and writes behind it: a Layout
        // is a presentation choice, costs no request, and needs no coroutine of this caller's.
        onSelectLayout = layout::choose,
        onSignOut = controls::signOut,
        onOpenAnime = animePages::open,
        animePage = AnimePageActions(
            onBack = animePages::back,
            onClose = animePages::close,
            onRetry = animePages::retry,
            onEdit = animePages::edit,
            onAdd = animePages::add,
            onOpenRelated = { animePages.open(it) },
        ),
        diagnostics = DiagnosticsActions(
            onReloadDiagnostics = { controls.reloadDiagnostics() },
            onRefreshUser = { controls.refreshUser() },
            onForceExpireAccessToken = { controls.forceExpireAccessToken() },
        ),
    ),
)

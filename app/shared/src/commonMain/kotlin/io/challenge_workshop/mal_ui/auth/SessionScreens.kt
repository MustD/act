package io.challenge_workshop.mal_ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.challenge_workshop.mal_ui.animelist.AnimeListFilters
import io.challenge_workshop.mal_ui.animelist.AnimeListSortRow
import io.challenge_workshop.mal_ui.animelist.filterLabel
import io.challenge_workshop.mal_ui.theme.Act
import io.challenge_workshop.mal_ui.theme.ActEasing
import io.challenge_workshop.mal_ui.animelist.LoadMoreWhenNearEnd
import io.challenge_workshop.mal_ui.animelist.animeListItems
import io.challenge_workshop.mal_ui.animelist.gridCells
import io.challenge_workshop.mal_ui.animelist.gridPadding
import io.challenge_workshop.mal_ui.animelist.gridSpacing
import io.challenge_workshop.mal_ui.animepage.AnimePage
import io.challenge_workshop.mal_ui.animepage.AnimePageScreen
import io.challenge_workshop.mal_ui.screen.ScreenState
import io.challenge_workshop.mal_ui.screen.ShownError

/**
 * Shown while the store is being read.
 *
 * Deliberately not a sign-in form: rendering one and then swapping it out for a signed-in screen is
 * exactly the flicker `Restoring` exists to prevent.
 */
@Composable
fun RestoringScreen(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * The waiting page: the user is away on myanimelist.net.
 *
 * Paste-the-code is collapsed under "Having trouble?" because it is needed only when the Redirect
 * Capture fails, and it is still always reachable. It is the one mechanism that works headless,
 * behind a blocked popup, and with no Custom-Tabs browser, so it is a modelled path — and the
 * authorization URL has to be reachable by hand because no platform's browser-opening call reliably
 * reports whether it worked.
 *
 * Open or closed is this composable's own state, not [ScreenState]'s. It opens by itself whenever a
 * sign-in error is shown, including a capture that failed outright (desktop port 18040 taken), since
 * that is exactly when the fallback is wanted.
 */
@Composable
fun AuthorizingScreen(
    state: ScreenState.Authorizing,
    actions: AuthorizingActions,
    modifier: Modifier = Modifier,
) {
    var troubleOpen by rememberSaveable { mutableStateOf(false) }
    // Keyed on the error itself, not on whether there is one: a different error replacing one the
    // user already closed the section on reopens it.
    LaunchedEffect(state.signInError) { if (state.signInError != null) troubleOpen = true }

    ScreenColumn(modifier) {
        Text("Waiting for MyAnimeList…", style = MaterialTheme.typography.headlineSmall)
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(
            "Finish signing in in the browser window that opened.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = actions.onCancelSignIn, enabled = !state.signIn.busy) { Text("Cancel") }
            if (state.signIn.busy) CircularProgressIndicator(Modifier.padding(4.dp))
        }

        state.signInError?.let { ErrorCard("Could not complete the sign-in", it.withRelayAdvice()) }

        HorizontalDivider()
        TextButton(onClick = { troubleOpen = !troubleOpen }) { Text("Having trouble?") }
        if (troubleOpen) {
            Text(
                "If you land on a page that will not load, copy the whole address from the address bar " +
                    "and paste it below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            @Suppress("DEPRECATION")
            // `LocalClipboard` supersedes this, but its `ClipEntry` has no common constructor from text
            // in Compose 1.11 — a copy button through it would need three actuals to write a string.
            val clipboard = LocalClipboardManager.current
            OutlinedTextField(
                value = state.authorizationUrl,
                onValueChange = {},
                readOnly = true,
                label = { Text("Authorization URL") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Didn't open? Copy this and paste it into a browser.") },
                // Selecting a long URL out of a text field by hand is exactly the friction that makes
                // people give up on the fallback, and the fallback is the only mechanism that always
                // works. Never logged: under `plain` PKCE the code verifier is inside this string.
                trailingIcon = {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(state.authorizationUrl)) }) {
                        Text("Copy")
                    }
                },
            )
            OutlinedTextField(
                value = state.signIn.pastedRedirect,
                onValueChange = actions.onPastedRedirectChange,
                label = { Text("Redirect URL or authorization code") },
                enabled = !state.signIn.busy,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = actions.onCompleteSignIn, enabled = state.signIn.canComplete) {
                Text("Complete sign-in")
            }
        }
    }
}

/**
 * The signed-in screen, which **is** the Anime List.
 *
 * **One scroll container, and it is lazy.** The Anime List is unbounded now — scrolling near its end
 * fetches the next page — and a lazy layout cannot be nested inside a scrolling [Column], so the
 * chrome around the list became items in the grid rather than the grid becoming a child of the
 * chrome. That is also what gives the paging trigger a `LazyGridState` to read the last-visible
 * index off, which is the one signal that means the same thing on every Target.
 *
 * **One `LazyVerticalGrid` for both Layouts.** The dense Layout is the same grid at one column, so
 * the Layout changes the column count and nothing else — no second scroll state,
 * no second paging trigger, and no second copy of the five screen states.
 *
 * The Layout arrives on [state] like everything else. It is a remembered choice — read from the
 * store and written back there by `LayoutPreference` — so the toggle changes it by asking, not by
 * owning it. Switching it re-draws the entries already loaded and makes no request, which is why the
 * toggle in the bar is not disabled while a page is in flight and the other two controls are.
 *
 * **All of that chrome sits *above* the entries, and that is not a layout preference.** Anything
 * placed after them is unreachable on a real account: every scroll towards it enters the prefetch
 * zone, appends fifty more entries and pushes it further down, so it only arrives once the whole
 * list has been paged in. Above the entries it is always one scroll up, and scrolling up never
 * fetches anything.
 *
 * Everything that is *not* a query over the list has left the list entirely: the name, the Layout
 * toggle, Reload, Sign out and the debug panel are in [SignedInPromptRow], which neither scrolls nor
 * competes with the entries. The filter row and the Sort Order stay between the bar and the list,
 * because those two are the query.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignedInScreen(
    state: ScreenState.SignedIn,
    actions: SignedInActions,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    // Whether the diagnostics dialog is over the screen. A dialog, not a destination — see
    // [SESSION_DIAGNOSTICS_TAG].
    var diagnosticsOpen by remember { mutableStateOf(false) }
    val list = state.list
    val layout = state.layout

    // Whether to arm it is `:core`'s decision, not this screen's — see `AnimeListState.pagingArmed`.
    LoadMoreWhenNearEnd(
        gridState = gridState,
        loadedCount = list.entryCount,
        revision = list.revision,
        enabled = list.pagingArmed,
        onLoadMore = actions.onLoadMore,
    )

    // The replacement page has landed and the content underneath the user's scroll position has
    // been swapped out, so that position is into a list that no longer exists. Keyed on the pager's
    // revision rather than on the filter, because the scroll has to happen when the new page
    // *arrives*, not when the chip is tapped — the old entries are deliberately still on screen in
    // between.
    //
    // `requestScrollToItem`, not `scrollToItem`: it is applied by the very measure pass that first
    // lays the new entries out, so no frame is ever laid out with the new list at the old scroll
    // position. Suspending until after that frame instead would leave one — and a user who changed
    // filter from the bottom of a long list would be at the bottom of the new one for it, which is
    // exactly where `LoadMoreWhenNearEnd` fires and fetches a page nobody scrolled to.
    LaunchedEffect(list.revision) {
        if (list.revision > 0) gridState.requestScrollToItem(0)
    }

    val page = state.animePages.current
    val pageActions = actions.animePage
    val canGoBack = state.animePages.canGoBack

    // Measured rather than a window size class: the same decision the list's rows make about their
    // own width, and no dependency. The list's own state — its scroll position included — is hoisted
    // above this, so it survives the panel opening and closing and a resize across the line.
    BoxWithConstraints(modifier.fillMaxSize().safeContentPadding()) {
        val sideBySide = maxWidth >= SIDE_PANEL_MIN_WIDTH
        if (page != null && !sideBySide) {
            // Narrow: an open page is the whole screen, and closing it lands where the list was left.
            AnimePageScreen(
                page = page,
                canGoBack = canGoBack,
                actions = pageActions,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // The screen tag is on this wrapper rather than on the list, because the list carries its
            // own and a second `testTag` would replace it.
            Row(Modifier.fillMaxSize()) {
                if (sideBySide) {
                    WideSidebar(state, actions, onShowDiagnostics = { diagnosticsOpen = true })
                }
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    SignedInPromptRow(
                        state = state,
                        actions = actions,
                        onShowDiagnostics = { diagnosticsOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        wide = sideBySide,
                    )
                    AnimeListPane(state, actions, gridState, Modifier.weight(1f).fillMaxWidth(), tabs = !sideBySide)
                }
                if (sideBySide) {
                    AnimePageSidePanel(page, canGoBack, pageActions)
                }
            }
        }
    }

    if (diagnosticsOpen) {
        SessionDiagnosticsDialog(
            state = state,
            actions = actions.diagnostics,
            onDismiss = { diagnosticsOpen = false },
        )
    }
}

/** The window width from which the Anime Page sits beside the list instead of replacing it. */
internal val SIDE_PANEL_MIN_WIDTH = 840.dp

/** The Anime Page's width as a side panel; the list takes the rest. */
internal val SIDE_PANEL_WIDTH = 480.dp

/** How long the side panel takes to open or close. */
internal const val SIDE_PANEL_MS = 400

/** How far to the right the panel's content starts, and fades in from. */
private val SIDE_PANEL_SLIDE = 24.dp

/**
 * The Anime Page beside the list. Its *width* animates between 0 and [SIDE_PANEL_WIDTH], so the
 * list reflows as it goes, while the content is laid out at the full 480dp the whole time and is
 * only clipped — a page that re-wrapped on every frame of the animation would be unreadable.
 * The content fades in from [SIDE_PANEL_SLIDE] to the right as the width grows.
 *
 * Closing reverses it, which needs the page that was open: [page] is already null by then, so the
 * last one is held until the width is back at 0.
 */
@Composable
private fun AnimePageSidePanel(
    page: AnimePage?,
    canGoBack: Boolean,
    actions: AnimePageActions,
) {
    val held = remember { mutableStateOf(page) }
    if (page != null) held.value = page
    val width by animateDpAsState(
        if (page != null) SIDE_PANEL_WIDTH else 0.dp,
        tween(SIDE_PANEL_MS, easing = ActEasing),
        label = "sidePanelWidth",
    )
    val shown = held.value
    if (shown == null || width == 0.dp && page == null) return
    val progress = (width / SIDE_PANEL_WIDTH).coerceIn(0f, 1f)
    Row(Modifier.width(width).fillMaxHeight().clipToBounds()) {
        VerticalDivider()
        AnimePageScreen(
            page = shown,
            canGoBack = canGoBack,
            actions = actions,
            modifier = Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .width(SIDE_PANEL_WIDTH)
                .fillMaxHeight()
                .graphicsLayer {
                    alpha = progress
                    translationX = (1f - progress) * SIDE_PANEL_SLIDE.toPx()
                }
                .testTag(ANIME_PAGE_PANEL_TAG)
                .padding(16.dp),
        )
    }
}

/** The filter row, the Sort Order and the entries: everything that is a query over the Anime List. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnimeListPane(
    state: ScreenState.SignedIn,
    actions: SignedInActions,
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    tabs: Boolean = true,
) {
    val list = state.list
    val layout = state.layout
    Column(modifier) {
        // Outside the lazy grid, so the title and both query controls stay put while the list
        // scrolls under them. The title is the bare filter label — MAL's list is paged and nothing
        // here knows the totals, so there is no count to put beside it.
        Text(
            list.watchStatus.filterLabel(),
            style = Act.type.screenTitle,
            color = Act.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(ANIME_LIST_TITLE_TAG),
        )
        // The sidebar carries the Watch Statuses from the side-panel width.
        if (tabs) {
            AnimeListFilters(
                selected = list.watchStatus,
                // Both read the same decision, because both go through the same reset — see
                // `AnimeListState.queryControlsEnabled`.
                enabled = list.queryControlsEnabled,
                onSelect = actions.onSelectWatchStatus,
            )
        }
        AnimeListSortRow(
            selected = list.sortOrder,
            enabled = list.queryControlsEnabled,
            onSelect = actions.onSelectSortOrder,
        )
        // Above the list rather than at the bottom of it, so a failed sign-out, Reload or profile
        // reload is visible from where the user actually is — and outside the grid, so it does not
        // scroll away from the controls that caused it.
        state.error?.let {
            ErrorCard("Something went wrong", it.withRelayAdvice(), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        LazyVerticalGrid(
            // The Layout is entirely this: how many columns the entries get. Everything inside `animeListItems` is written once.
            columns = layout.gridCells(),
            // `weight`, not `fillMaxSize`: a child that fills the height inside a `Column` takes
            // the whole window and hangs the last entries of the list below the bottom of it,
            // because the filter row above has already taken its share.
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag(ANIME_LIST_TAG),
            state = gridState,
            contentPadding = layout.gridPadding(),
            horizontalArrangement = Arrangement.spacedBy(layout.gridSpacing()),
            verticalArrangement = Arrangement.spacedBy(layout.gridSpacing()),
        ) {
            animeListItems(
                state = list,
                layout = layout,
                onRetry = actions.onRetry,
                // "Show all" is the same gesture as tapping the All chip, and goes through the same
                // reset — an empty slice's way out must not become a second way of changing filter.
                onShowAll = { actions.onSelectWatchStatus(null) },
                onOpen = actions.onOpenAnime,
                openAnimeId = state.animePages.current?.animeId,
            )
        }
    }
}

@Composable
internal fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.paneItem(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
    }
}

/**
 * The width a sign-in pane gets: as wide as the window, up to a line length that is still readable
 * on a desktop or a browser maximised across a monitor. The Anime List does not use it — it fills
 * the window.
 */
internal fun Modifier.paneItem(): Modifier = widthIn(max = PANE_MAX_WIDTH).fillMaxWidth()

/** One line length, in one place: the sign-in panes. The Anime List is not capped — it fills the window. */
internal val PANE_MAX_WIDTH = 560.dp

/**
 * On web a dead relay surfaces as a bare "Failed to fetch"; when `:core` says that is the likely
 * cause ([ShownError.relayHint]), say so in words a user can act on. The predicate is `:core`'s, the wording is here.
 */
internal fun ShownError.withRelayAdvice(): String =
    if (relayHint) {
        "$message\n\nCouldn't reach the server. Please try again later."
    } else {
        message
    }

/** Internal, not private: the Anime List reuses it rather than growing an error card of its own. */
@Composable
internal fun ErrorCard(title: String, body: String, modifier: Modifier = Modifier) {
    MessageCard(
        modifier = modifier,
        title = title,
        body = body,
        container = MaterialTheme.colorScheme.errorContainer,
        content = MaterialTheme.colorScheme.onErrorContainer,
    )
}

@Composable
internal fun MessageCard(
    title: String,
    body: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun LabelledValue(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodySmall, overflow = TextOverflow.Ellipsis, maxLines = 1)
    }
}

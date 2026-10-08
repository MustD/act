package io.challenge_workshop.mal_ui.auth

import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import io.challenge_workshop.mal_ui.theme.rememberSpinnerGlyph
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.height
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import io.challenge_workshop.mal_ui.getPlatform
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import io.challenge_workshop.mal_ui.animelist.AnimeListContent
import io.challenge_workshop.mal_ui.animelist.AnimeListLayout
import io.challenge_workshop.mal_ui.animepage.ListEdit
import io.challenge_workshop.mal_ui.animepage.KeyCommand
import io.challenge_workshop.mal_ui.animepage.adjacentIndex
import io.challenge_workshop.mal_ui.animepage.keyCommand
import io.challenge_workshop.mal_ui.animepage.steppedWatchStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import io.challenge_workshop.mal_ui.theme.motion
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
    Box(modifier.fillMaxSize().background(Act.colors.bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ActWordmark(size = 32)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${rememberSpinnerGlyph()} restoring session", style = Act.type.meta, color = Act.colors.dim)
            }
        }
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
        ActWordmark()
        PromptLine("mal auth --wait")
        Text("Waiting for MyAnimeList…", style = Act.type.pageTitle, color = Act.colors.ink)
        TerminalProgress(Modifier.fillMaxWidth())
        Text(
            "Finish signing in in the browser window that opened.",
            style = Act.type.body,
            color = Act.colors.dim,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TerminalButton("Cancel", onClick = actions.onCancelSignIn, enabled = !state.signIn.busy)
            if (state.signIn.busy) Text(rememberSpinnerGlyph().toString(), style = Act.type.body, color = Act.colors.pend)
        }

        state.signInError?.let { ErrorCard("Could not complete the sign-in", it.withRelayAdvice()) }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Act.colors.ln))
        TerminalLink("Having trouble?", onClick = { troubleOpen = !troubleOpen })
        if (troubleOpen) {
            Text(
                "If you land on a page that will not load, copy the whole address from the address bar " +
                    "and paste it below.",
                style = Act.type.body,
                color = Act.colors.dim,
            )

            @Suppress("DEPRECATION")
            // `LocalClipboard` supersedes this, but its `ClipEntry` has no common constructor from text
            // in Compose 1.11 — a copy button through it would need three actuals to write a string.
            val clipboard = LocalClipboardManager.current
            TerminalField(
                value = state.authorizationUrl,
                onValueChange = {},
                readOnly = true,
                label = "Authorization URL",
                hint = "Didn't open? Copy this and paste it into a browser.",
            )
            // Selecting a long URL out of a text field by hand is exactly the friction that makes
            // people give up on the fallback, and the fallback is the only mechanism that always
            // works. Never logged: under `plain` PKCE the code verifier is inside this string.
            TerminalButton("Copy", onClick = { clipboard.setText(AnnotatedString(state.authorizationUrl)) })
            TerminalField(
                value = state.signIn.pastedRedirect,
                onValueChange = actions.onPastedRedirectChange,
                label = "Redirect URL or authorization code",
                enabled = !state.signIn.busy,
                minLines = 2,
            )
            TerminalButton("Complete sign-in", onClick = actions.onCompleteSignIn, enabled = state.signIn.canComplete, primary = true)
        }
    }
}

/**
 * Where the sweeping segment is, 0…1. Reduced motion parks it mid-rule — still an indeterminate bar, but nothing
 * travels, and no infinite transition is left running to ask for frames.
 */
@Composable
private fun sweepPosition(): Float {
    if (Act.reducedMotion) return 0.5f
    val at by rememberInfiniteTransition(label = "sweep")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "at")
    return at
}

/** An indeterminate bar: a 2dp rule in `ln` with an accent segment sweeping along it. */
@Composable
private fun TerminalProgress(modifier: Modifier = Modifier) {
    val c = Act.colors
    val at = sweepPosition()
    Box(modifier.height(2.dp).background(c.ln).drawBehind {
        val w = size.width * 0.25f
        val x = (size.width + w) * at - w
        drawRect(c.acc, Offset(x.coerceAtLeast(0f), 0f), Size((x + w).coerceAtMost(size.width) - x.coerceAtLeast(0f), size.height))
    })
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
    val keys = remember { FocusRequester() }
    // Again when the page closes: the node that held focus leaves composition with it.
    // Not on opening: the page takes focus itself, for Esc, and must keep it.
    LaunchedEffect(state.animePages.isOpen) { if (!state.animePages.isOpen) runCatching { keys.requestFocus() } }
    val scope = rememberCoroutineScope()
    // No text field lives under this screen (Paste-the-code is on the Authorizing screen and the date
    // picker is its own dialog window), so there is nothing for these keys to steal from.
    val keyboard = getPlatform().hasKeyboard
    BoxWithConstraints(
        modifier.fillMaxSize().safeContentPadding().focusRequester(keys).focusTarget()
            .onPreviewKeyEvent { event ->
                if (!keyboard || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Browser and OS shortcuts (ctrl+- zoom, alt+arrows) are not ours. Shift stays: `+` is shift-`=`.
                if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return@onPreviewKeyEvent false
                val command = keyCommand(event.key) ?: return@onPreviewKeyEvent false
                handleKeyCommand(command, state, actions, gridState, scope)
                true
            },
    ) {
        val sideBySide = maxWidth >= SIDE_PANEL_MIN_WIDTH
        val phoneProgress by animateFloatAsState(
            if (page != null && !sideBySide) 1f else 0f,
            motion(tween(SIDE_PANEL_MS, easing = ActEasing)),
            label = "phonePage",
        )
        // Held so that closing can run the slide backwards: `page` is already null by then.
        val heldPhonePage = remember { mutableStateOf(page) }
        if (page != null) heldPhonePage.value = page
        else if (phoneProgress == 0f) heldPhonePage.value = null
        Column(Modifier.fillMaxSize()) {
            // The page's slide-in stays inside this box, so the bar below it never slides away with it.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // The list is always laid out; on a phone the page slides in over it and the list steps back.
                Row(
                    Modifier.fillMaxSize().graphicsLayer {
                        if (!sideBySide) {
                            translationX = -PHONE_LIST_SHIFT * size.width * phoneProgress
                            alpha = 1f - (1f - PHONE_LIST_DIM) * phoneProgress
                        }
                    },
                ) {
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
                val shown = heldPhonePage.value
                if (!sideBySide && shown != null && (page != null || phoneProgress > 0f)) {
                    AnimePageScreen(
                        page = shown,
                        canGoBack = canGoBack,
                        actions = pageActions,
                        open = page != null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationX = (1f - phoneProgress) * size.width }
                            .shadow(24.dp)
                            .background(Act.colors.bg)
                            // The list is still underneath: a node that listens for pointers, even to nothing,
                            // is what stops the page's empty areas letting a tap fall through to it.
                            .pointerInput(Unit) {},
                    )
                }
            }
            LogBar(state.saveLog, keyHints = sideBySide && keyboard)
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

/**
 * One bound key. Moves go through [SignedInActions.onOpenAnime], edits through the page's own
 * [AnimePageActions.onEdit], so the save loop and [AnimePage.canEdit] apply as for a tap.
 */
private fun handleKeyCommand(
    command: KeyCommand,
    state: ScreenState.SignedIn,
    actions: SignedInActions,
    gridState: LazyGridState,
    scope: CoroutineScope,
) {
    val page = state.animePages.current
    when (command) {
        is KeyCommand.Move -> {
            val entries = (state.list.content as? AnimeListContent.Entries)?.entries ?: return
            val current = entries.indexOfFirst { it.animeId == page?.animeId }
            val next = adjacentIndex(current, command.delta, entries.size) ?: return
            // At the end of what is loaded, `j` asks for more, as scrolling would.
            if (command.delta > 0 && next == current) actions.onLoadMore()
            if (next == current) return
            actions.onOpenAnime(entries[next])
            val offset = if (state.layout == AnimeListLayout.Table) 1 else 0
            scope.launch { gridState.animateScrollToItem(next + offset) }
        }

        is KeyCommand.Edit -> if (page?.canEdit == true && page.wouldChange(command.edit)) actions.animePage.onEdit(command.edit)

        is KeyCommand.StepWatchStatus -> {
            val entry = page?.shownListEntry?.takeIf { page.canEdit } ?: return
            val stepped = steppedWatchStatus(entry.watchStatus, command.delta)
            if (stepped != entry.watchStatus) actions.animePage.onEdit(ListEdit.SetWatchStatus(stepped))
        }
    }
}

/** How far, as a fraction of its width, the list steps back while a phone's page is over it, and how dim it gets. */
private const val PHONE_LIST_SHIFT = 0.22f
private const val PHONE_LIST_DIM = 0.35f

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
        motion(tween(SIDE_PANEL_MS, easing = ActEasing)),
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
            sidePanel = true,
            open = page != null,
            modifier = Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .width(SIDE_PANEL_WIDTH)
                .fillMaxHeight()
                .graphicsLayer {
                    alpha = progress
                    translationX = (1f - progress) * SIDE_PANEL_SLIDE.toPx()
                }
                .testTag(ANIME_PAGE_PANEL_TAG),
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
            .background(Act.colors.bg)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.paneItem(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
    TerminalMessage(title, body, modifier)
}

@Composable
internal fun LabelledValue(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label:", style = Act.type.meta, color = Act.colors.dim)
        Text(value, style = Act.type.meta, color = Act.colors.ink, overflow = TextOverflow.Ellipsis, maxLines = 1)
    }
}

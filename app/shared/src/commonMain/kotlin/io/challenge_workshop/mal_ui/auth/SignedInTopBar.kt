package io.challenge_workshop.mal_ui.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import io.challenge_workshop.mal_ui.theme.BusyIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.challenge_workshop.mal_ui.animelist.AnimeListLayout
import io.challenge_workshop.mal_ui.animelist.layoutLabel
import io.challenge_workshop.mal_ui.animelist.next
import io.challenge_workshop.mal_ui.animelist.promptText
import io.challenge_workshop.mal_ui.theme.ActMedium
import io.challenge_workshop.mal_ui.theme.SectionLabel
import io.challenge_workshop.mal_ui.screen.ScreenState
import io.challenge_workshop.mal_ui.theme.Act
import io.challenge_workshop.mal_ui.theme.ActMedium
import kotlinx.coroutines.delay
import mal_ui.app.shared.generated.resources.Res
import mal_ui.app.shared.generated.resources.dark_mode
import mal_ui.app.shared.generated.resources.light_mode
import mal_ui.app.shared.generated.resources.more_vert
import mal_ui.app.shared.generated.resources.table_rows
import mal_ui.app.shared.generated.resources.view_agenda
import mal_ui.app.shared.generated.resources.view_list
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * The prompt row over the signed-in screen: `<user>@mal:~$ ls <watch status>/` on the left, and on
 * the right the three things that are not a query over the list — the Layout, the Theme, and a
 * `more_vert` menu holding the three things the Anime List taking over the screen could otherwise
 * have cost. Reload is the way to pick up a change made on myanimelist.net, sign out is the only way
 * back to the sign-in screen, and diagnostics is the only way a human ever sees the refresh path
 * execute. Nothing was deleted to make room.
 *
 * It replaces the top app bar, which neither scrolled nor competed with the list and so was the one
 * place the name could sit; the row is outside the lazy grid for the same reason.
 *
 * `state.refreshing` is the **Session** refreshing, not the list: it shows as a spinner beside the
 * buttons precisely because a refresh must not unmount this screen. The list's own loading lives in
 * the list. A failed sign-out, Reload or profile reload is an [ErrorCard] under the row, drawn by
 * the pane.
 *
 * The Layout button shows the Layout the screen is in now and moves to the next; the Theme button
 * shows the Theme it would switch to, as the design does.
 */
@Composable
fun SignedInPromptRow(
    state: ScreenState.SignedIn,
    actions: SignedInActions,
    onShowDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val c = Act.colors

    Row(
        modifier = modifier.testTag(SESSION_TOP_BAR_TAG).padding(start = 16.dp, top = 6.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (wide) {
            // The prompt is in the sidebar at this width; this row keeps only the buttons.
            Spacer(Modifier.weight(1f))
        } else {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    promptText(state.user?.name, state.list.watchStatus),
                    style = Act.type.meta,
                    color = c.dim,
                    // A MAL username has no length this row can rely on, and a row that grew a second
                    // line for one would push the list down by exactly as much.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).testTag(SESSION_USER_NAME_TAG),
                )
                BlockCursor(Modifier.padding(start = 2.dp))
            }
        }
        if (state.refreshing) {
            BusyIndicator(Modifier.padding(horizontal = 8.dp))
        }
        // A button that moves to the next Layout, rather than a menu of them: it re-draws the
        // entries already loaded and asks MAL for nothing, which is also why it is never disabled
        // while a page is in flight.
        PromptIconButton(
            icon = state.layout.icon(),
            description = "Layout: ${state.layout.layoutLabel()}. Change layout",
            onClick = { actions.onSelectLayout(state.layout.next()) },
            modifier = Modifier.testTag(ANIME_LIST_LAYOUT_TAG),
        )
        PromptIconButton(
            icon = if (c.isDark) Res.drawable.light_mode else Res.drawable.dark_mode,
            description = if (c.isDark) "Switch to light theme" else "Switch to dark theme",
            onClick = { actions.onToggleTheme(c.isDark) },
            modifier = Modifier.testTag(SESSION_THEME_TAG),
        )
        // At wide widths the sidebar carries these three; the menu would only repeat it.
        if (!wide) {
            // The menu is anchored to this `Box` rather than to the row, so it opens under the button
            // that summoned it instead of at the corner of the window.
            Box {
                PromptIconButton(
                    icon = Res.drawable.more_vert,
                    description = "More",
                    onClick = { menuOpen = true },
                    modifier = Modifier.testTag(SESSION_MENU_BUTTON_TAG),
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.testTag(SESSION_MENU_TAG),
                ) {
                    // Each entry closes the menu before it acts. Leaving it open over a screen that has
                    // just started replacing itself would put a second tap one pixel away from the first.
                    DropdownMenuItem(
                        text = { Text("reload") },
                        // The same reset the filter and the Sort Order go through, with neither of them
                        // changed — so it refetches the list on screen from `offset=0`.
                        onClick = {
                            menuOpen = false
                            actions.onReload()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("diagnostics") },
                        onClick = {
                            menuOpen = false
                            onShowDiagnostics()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("sign out") },
                        enabled = !state.busy,
                        onClick = {
                            menuOpen = false
                            actions.onSignOut()
                        },
                    )
                }
            }
        }
    }
}

/** The Layout the button currently shows: the icon of the arrangement on screen. */
private fun AnimeListLayout.icon(): DrawableResource = when (this) {
    AnimeListLayout.Cards -> Res.drawable.view_agenda
    AnimeListLayout.List -> Res.drawable.view_list
    AnimeListLayout.Table -> Res.drawable.table_rows
}

/** A 40dp square icon button, with the 40dp hit target the design asks for. */
@Composable
private fun PromptIconButton(
    icon: DrawableResource,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(40.dp).clip(ActMedium).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Act.colors.ink, modifier = Modifier.size(20.dp))
    }
}

/** The 7×12 accent block after the prompt, on for half a second and off for half a second. */
@Composable
internal fun BlockCursor(modifier: Modifier = Modifier) {
    var on by remember { mutableStateOf(true) }
    // Reduced motion leaves the cursor on, solid.
    val still = Act.reducedMotion
    LaunchedEffect(still) {
        on = true
        while (!still) {
            delay(CURSOR_BLINK_MS)
            on = !on
        }
    }
    Box(modifier.size(7.dp, 12.dp).alpha(if (on) 1f else 0f).background(Act.colors.acc))
}

private const val CURSOR_BLINK_MS = 500L

/**
 * [SessionDebugPanel] in a dialog, which is where the overflow menu's last entry opens it.
 *
 * A dialog and **not a third destination** — see [SESSION_DIAGNOSTICS_TAG]. The signed-in screen is
 * still underneath, with its scroll position and its loaded pages intact.
 *
 * The menu entry is the panel's disclosure now, which is why the panel itself no longer carries one —
 * a dialog that opened onto a collapsed panel would be two taps to say one thing.
 *
 * Scrollable, because the panel is a dozen diagnostic lines and a phone in landscape is shorter than
 * they are.
 *
 * The user's MAL id is here rather than in the bar because the bar has one line and the name has to
 * have it. The id is the half of the old profile row nobody reads until something is wrong — two
 * accounts on one device, or a name that was renamed — which is the definition of a diagnostic.
 */
@Composable
fun SessionDiagnosticsDialog(
    state: ScreenState.SignedIn,
    actions: DiagnosticsActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(SESSION_DIAGNOSTICS_TAG),
        containerColor = Act.colors.sf,
        shape = ActMedium,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel("diagnostics")
                Text("Session diagnostics", style = Act.type.sheetTitle, color = Act.colors.ink)
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LabelledValue(
                    "Signed in as",
                    state.user?.let { "${it.name} (MAL id ${it.id})" } ?: "—",
                )
                SessionDebugPanel(state, actions)
            }
        },
        confirmButton = { TerminalButton("Close", onClick = onDismiss) },
    )
}

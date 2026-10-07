package io.challenge_workshop.mal_ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.challenge_workshop.mal_ui.animelist.ANIME_LIST_FILTERS
import io.challenge_workshop.mal_ui.animelist.WatchStatus
import io.challenge_workshop.mal_ui.animelist.promptText
import io.challenge_workshop.mal_ui.animelist.tabKey
import io.challenge_workshop.mal_ui.screen.ScreenState
import io.challenge_workshop.mal_ui.theme.Act
import io.challenge_workshop.mal_ui.theme.ActMedium
import io.challenge_workshop.mal_ui.theme.enabledAlpha

/**
 * The sidebar from [SIDE_PANEL_MIN_WIDTH]: the prompt, the name, the six Watch Statuses and the
 * three things the narrow screen keeps behind `more_vert`.
 *
 * It replaces the tabs and the More menu rather than sitting beside them, so each Watch Status and
 * each session action has exactly one place on screen at any width. The Watch Status items carry
 * [ANIME_LIST_FILTERS_TAG], which the tabs carry below the line; [enabled] is the same
 * `queryControlsEnabled` decision the tabs read.
 */
@Composable
internal fun WideSidebar(
    state: ScreenState.SignedIn,
    actions: SignedInActions,
    onShowDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Act.colors
    val ln = c.ln
    Column(
        modifier
            .testTag(SESSION_SIDEBAR_TAG)
            .width(SIDEBAR_WIDTH)
            .fillMaxHeight()
            .background(c.sf)
            .drawBehind {
                drawLine(ln, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
            }
            .padding(start = 10.dp, top = 14.dp, end = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                promptText(state.user?.name, state.list.watchStatus),
                style = Act.type.meta,
                color = c.dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).testTag(SESSION_USER_NAME_TAG),
            )
            BlockCursor(Modifier.padding(start = 2.dp))
        }
        Text(
            buildAnnotatedString {
                append("ACT")
                withStyle(SpanStyle(color = c.acc)) { append("_") }
            },
            style = Act.type.sheetTitle.copy(
                fontFamily = Act.type.meta.fontFamily,
                fontSize = 22.sp,
                lineHeight = 28.sp,
            ),
            color = c.ink,
            modifier = Modifier.padding(start = 8.dp, top = 2.dp, end = 8.dp, bottom = 14.dp),
        )
        SidebarLabel("// watch status")
        Column(Modifier.testTag(ANIME_LIST_FILTERS_TAG), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (filter in ANIME_LIST_FILTERS) {
                SidebarWatchStatus(
                    filter,
                    active = filter == state.list.watchStatus,
                    enabled = state.list.queryControlsEnabled,
                ) { actions.onSelectWatchStatus(filter) }
            }
        }
        Spacer(Modifier.weight(1f))
        SidebarLabel("// session")
        SidebarAction("reload", enabled = true, onClick = actions.onReload)
        SidebarAction("diagnostics", enabled = true, onClick = onShowDiagnostics)
        SidebarAction("sign out", enabled = !state.busy, onClick = actions.onSignOut)
    }
}

@Composable
private fun SidebarLabel(text: String) {
    Text(
        text,
        style = Act.type.sectionLabel,
        color = Act.colors.dim,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
    )
}

@Composable
private fun SidebarWatchStatus(filter: WatchStatus?, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = Act.colors
    Box(
        Modifier.fillMaxWidth().height(38.dp).clip(ActMedium)
            .background(if (active) c.sf2 else Color.Transparent)
            .selectable(selected = active, enabled = enabled, role = Role.Tab, onClick = onClick)
            .enabledAlpha(enabled, 0.5f)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            "${if (active) "▸" else " "} ${filter.tabKey()}",
            style = Act.type.body,
            color = if (active) c.acc else c.dim,
            maxLines = 1,
        )
    }
}

@Composable
private fun SidebarAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(34.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .enabledAlpha(enabled, 0.5f)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = Act.type.meta.copy(fontSize = 12.sp, lineHeight = 16.sp), color = Act.colors.dim)
    }
}

/** The sidebar's width. */
internal val SIDEBAR_WIDTH = 208.dp

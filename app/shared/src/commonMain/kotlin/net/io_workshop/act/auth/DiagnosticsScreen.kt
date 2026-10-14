package net.io_workshop.act.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.io_workshop.act.ACT_VERSION
import net.io_workshop.act.screen.ScreenState
import net.io_workshop.act.theme.Act

/**
 * The Diagnostics Screen, in its two signed-in frames: full screen with a back control on a narrow
 * window, and beside the sidebar on a wide one, where [onBack] is null because the sidebar is the way out.
 *
 * The diagnostics are loaded when the Screen opens, so it never shows the empty state for a Session
 * that has one; "Reload diagnostics" is for reading them again after something changed.
 *
 * **No token value ever appears here** — see [SessionDebugPanel].
 */
@Composable
fun DiagnosticsScreen(
    state: ScreenState.SignedIn,
    actions: DiagnosticsActions,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { actions.onReloadDiagnostics() }
    ScreenColumn(modifier.testTag(SESSION_DIAGNOSTICS_TAG)) {
        if (onBack != null) {
            TerminalLink("← back", onClick = onBack, modifier = Modifier.testTag(DIAGNOSTICS_BACK_TAG))
        }
        ActWordmark(size = 32)
        PromptLine("act --diagnostics")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Session diagnostics", style = Act.type.pageTitle, color = Act.colors.ink)
            Text(
                "ACT $ACT_VERSION",
                style = Act.type.meta,
                color = Act.colors.dim,
                modifier = Modifier.testTag(DIAGNOSTICS_VERSION_TAG),
            )
        }
        // The user's MAL id is here rather than in the prompt row: it is the half nobody reads until
        // something is wrong — two accounts on one device, or a renamed one.
        LabelledValue("Signed in as", state.user?.let { "${it.name} (MAL id ${it.id})" } ?: "—")
        SessionDebugPanel(state, actions)
    }
}

package net.io_workshop.act.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.io_workshop.act.getPlatform
import net.io_workshop.act.screen.ScreenState
import net.io_workshop.act.theme.Act

/**
 * The Diagnostics Screen's lines and buttons.
 *
 * Everything in here answers a question that is otherwise unanswerable from inside a running build: a
 * misrouted web build silently talks to the wrong host, a Redirect URI mismatch reports as a 401 about
 * the Client ID, and nothing in a shell-only app ever crosses the one-hour access-token boundary.
 *
 * **No token value ever appears here.** [net.io_workshop.act.session.SessionDiagnostics] has
 * no field that could carry one, so that stays true however this panel is edited later.
 *
 * It carries no expand/collapse of its own: [DiagnosticsScreen] holds it, and the Main Menu's
 * `diagnostics` entry is the disclosure. "Force 401" is still behind that one pick, which is what
 * keeps it off the screen a stray finger is on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionDebugPanel(
    state: ScreenState.SignedIn,
    actions: DiagnosticsActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LabelledValue("Target", getPlatform().name)
        LabelledValue("Token endpoint", state.routing.endpoints.tokenEndpoint)
        LabelledValue("API base", state.routing.endpoints.apiBaseUrl)
        LabelledValue("Redirect URI", state.routing.redirectUri)
        LabelledValue("Via relay", if (state.routing.usesRelay) "yes" else "no — MAL directly")

        val diagnostics = state.diagnostics
        if (diagnostics == null) {
            Text(
                "No stored Session.",
                style = Act.type.meta,
                color = Act.colors.dim,
            )
        } else {
            LabelledValue("Access token obtained", "${diagnostics.obtainedAtEpochMs} ms epoch")
            LabelledValue("Access token age", "${diagnostics.ageMillis / 1000}s")
            LabelledValue("Access token length", "${diagnostics.accessTokenLength} chars")
            LabelledValue("Refresh token stored", if (diagnostics.hasRefreshToken) "yes" else "no")
            if (diagnostics.accessTokenIsDeliberatelyInvalid) {
                Text(
                    "The access token has been deliberately invalidated. The next request should " +
                        "get a real 401 from MAL and refresh.",
                    style = Act.type.meta,
                    color = Act.colors.dim,
                )
            }
        }

        // A `FlowRow`, because four buttons of this width do not fit a phone side by side and a
        // `Row` would clip the last of them off the edge with no way to reach it.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TerminalButton("Reload diagnostics", onClick = actions.onReloadDiagnostics, enabled = !state.busy)
            // Here rather than on the screen, and next to "Force 401" rather than anywhere else:
            // reloading the profile is the request that makes a forced 401 refresh, and the two
            // being one row apart is the whole procedure.
            TerminalButton("Reload profile", onClick = actions.onRefreshUser, enabled = !state.busy)
            // The only way a human ever sees the refresh path execute against real MAL: a shell-only
            // app never sits open for the hour it would otherwise take.
            TerminalButton("Force 401", onClick = actions.onForceExpireAccessToken, enabled = !state.busy)
            // Reload, the Anime List's: the same reset a filter or Sort Order change goes through, with
            // neither changed. Not gated on `busy`, which is the Session's operation and not the list's.
            TerminalButton("Reload list", onClick = actions.onReloadList)
        }
        Text(
            "Force 401 writes an invalid access token, keeps the refresh token, and drops Ktor's " +
                "cached copy. Reload profile afterwards to watch the refresh happen.",
            style = Act.type.meta,
            color = Act.colors.dim,
        )
    }
}

package net.io_workshop.act.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import net.io_workshop.act.ACT_VERSION
import net.io_workshop.act.getPlatform
import net.io_workshop.act.theme.Act

/** MAL's own Supporter page. */
const val SUPPORT_MAL_URL: String = "https://myanimelist.net/membership"
const val SUPPORT_DEVELOPMENT_URL: String = "https://patreon.com/MustD"
const val GITHUB_URL: String = "https://github.com/MustD/act"
const val REPORT_ISSUE_URL: String = "$GITHUB_URL/issues"
const val MAL_HOME_URL: String = "https://myanimelist.net"
const val MAL_REGISTER_URL: String = "https://myanimelist.net/register.php"

/** The privacy policy, on the production origin on every Target: there is no in-app copy of it. */
const val PRIVACY_URL: String = "https://act.io-workshop.net/privacy"

/**
 * The About Screen's content, one composable for its three frames: signed out, signed in narrow (full
 * screen) and signed in wide (beside the sidebar). A frame is only how much of the window it gets and
 * whether it has a back control, so [onBack] is null where the sidebar is the way out.
 */
@Composable
fun AboutScreen(onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    ScreenColumn(modifier.testTag(ABOUT_SCREEN_TAG)) {
        if (onBack != null) {
            TerminalLink("← back", onClick = onBack, modifier = Modifier.testTag(ABOUT_BACK_TAG))
        }
        ActWordmark(size = 32)
        PromptLine("act --about")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("About ACT", style = Act.type.pageTitle, color = Act.colors.ink)
            Text("Anime Control Terminal", style = Act.type.body, color = Act.colors.dim)
        }
        Text(
            "Browse and update your MyAnimeList anime list.",
            style = Act.type.synopsis,
            color = Act.colors.ink,
        )
        Text(
            "ACT $ACT_VERSION · ${getPlatform().name}",
            style = Act.type.meta,
            color = Act.colors.dim,
            modifier = Modifier.testTag(ABOUT_VERSION_TAG),
        )

        PaneSection("links") {
            Column {
                TerminalLink("GitHub", onClick = { uriHandler.openUri(GITHUB_URL) })
                TerminalLink("Report an issue", onClick = { uriHandler.openUri(REPORT_ISSUE_URL) })
                TerminalLink("Support MyAnimeList", onClick = { uriHandler.openUri(SUPPORT_MAL_URL) })
                TerminalLink("Support development", onClick = { uriHandler.openUri(SUPPORT_DEVELOPMENT_URL) })
                TerminalLink("Privacy policy", onClick = { uriHandler.openUri(PRIVACY_URL) })
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Unofficial — not affiliated with or endorsed by MyAnimeList.",
                style = Act.type.meta,
                color = Act.colors.dim,
            )
            TerminalLink("Anime data provided by MyAnimeList.net", onClick = { uriHandler.openUri(MAL_HOME_URL) })
        }

        PaneSection("licences") { LicenceList() }
    }
}

/**
 * The signed-out frame: [AboutScreen] with its back link, and Esc and the Android back gesture both
 * returning to the Welcome Screen. The signed-in frames get theirs from `SignedInScreen`, which already
 * owns the keyboard.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SignedOutAboutScreen(actions: WelcomeActions, modifier: Modifier = Modifier) {
    BackHandler(onBack = actions.onBack)
    // Esc needs focus to be heard.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        modifier.fillMaxSize().focusRequester(focus).focusTarget().onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                actions.onBack()
                true
            } else {
                false
            }
        },
    ) {
        AboutScreen(onBack = actions.onBack)
    }
}

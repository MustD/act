package io.challenge_workshop.mal_ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.challenge_workshop.mal_ui.screen.ScreenState
import io.challenge_workshop.mal_ui.theme.Act
import io.challenge_workshop.mal_ui.theme.rememberSpinnerGlyph

/**
 * The signed-out screen. There is no password field and never will be: MAL supports only the
 * authorization code grant, so the password is typed on myanimelist.net and this app only ever sees
 * a code. There is no Client ID field either — the build's is the only one.
 */
@Composable
fun WelcomeScreen(
    state: ScreenState.SignedOut,
    actions: WelcomeActions,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    ScreenColumn(modifier) {
        ActWordmark(size = 32)
        PromptLine("mal login")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Welcome to ACT", style = Act.type.pageTitle, color = Act.colors.ink)
            Text("Anime Control Terminal", style = Act.type.body, color = Act.colors.dim)
        }
        Text(
            "Browse and update your MyAnimeList anime list.",
            style = Act.type.synopsis,
            color = Act.colors.ink,
        )

        // Absent for a first visit: `:core` hands over `null` rather than a line saying nothing.
        state.explanation?.let {
            Text(
                // The copy is the state's, not this screen's: what separates the Signed Out Reasons
                // is exactly what they say, and `:core`'s mapping test compares them in one place on
                // every Target.
                it,
                style = Act.type.body,
                color = Act.colors.dim,
                modifier = Modifier.testTag(SIGNED_OUT_REASON_TAG),
            )
        }

        if (state.signIn.clientIdMissing) {
            Text(
                "This build has no Client ID, so signing in is unavailable.",
                style = Act.type.body,
                color = Act.colors.error,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Called straight from the click and not out of a `launch { }`: a web popup's user
            // activation is a timestamp window, and WebKit's is one second wide. A lambda hop is
            // synchronous, so routing this through an actions record does not spend any of it —
            // `PopupUserActivationTest` is what holds that to the production dispatcher.
            TerminalButton("Sign in with MyAnimeList", onClick = actions.onSignIn, enabled = state.signIn.canStart, primary = true)
            if (state.signIn.busy) Text(rememberSpinnerGlyph().toString(), style = Act.type.body, color = Act.colors.pend)
        }

        state.error?.let { ErrorCard("Sign-in failed", it.withRelayAdvice()) }
        state.signInError?.let { ErrorCard("Sign-in failed", it.withRelayAdvice()) }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("A MyAnimeList account is required.", style = Act.type.body, color = Act.colors.dim)
            TerminalLink(
                "Create one on myanimelist.net",
                onClick = { uriHandler.openUri(MAL_REGISTER_URL) },
                modifier = Modifier.testTag(WELCOME_REGISTER_TAG),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TerminalLink("about", onClick = actions.onOpenAbout, modifier = Modifier.testTag(WELCOME_ABOUT_TAG))
            TerminalLink("privacy", onClick = { uriHandler.openUri(PRIVACY_URL) }, modifier = Modifier.testTag(WELCOME_PRIVACY_TAG))
        }

        Text(
            "Unofficial \u2014 not affiliated with or endorsed by MyAnimeList.",
            style = Act.type.meta,
            color = Act.colors.dim,
        )
    }
}

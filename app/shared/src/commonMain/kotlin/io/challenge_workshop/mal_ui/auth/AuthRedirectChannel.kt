package io.challenge_workshop.mal_ui.auth

import androidx.compose.runtime.Composable

/**
 * This target's [AuthRedirectChannel], scoped to the composition that will use it.
 *
 * Remember it **above** the `SessionState` `when` and not inside the sign-in screen: the screen is
 * swapped out for `AuthorizingScreen` the moment the flow starts, and a channel that left composition
 * there would take an Android `ActivityResultLauncher` with it.
 */
@Composable
expect fun rememberAuthRedirectChannel(): AuthRedirectChannel

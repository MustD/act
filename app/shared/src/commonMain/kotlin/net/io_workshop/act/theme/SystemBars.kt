package net.io_workshop.act.theme

import androidx.compose.runtime.Composable

/**
 * Makes the platform's system bars (Android's status and navigation bars) readable on a background
 * drawn [dark] or light, whatever the device's own theme says. Nothing on desktop or web.
 *
 * Called where the Theme is resolved, so a change of Theme restyles the bars without a restart.
 */
@Composable
expect fun SystemBarsFollowTheme(dark: Boolean)

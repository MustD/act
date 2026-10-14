package net.io_workshop.act.theme

import androidx.compose.runtime.Composable

/** Desktop has no reduce-motion setting to read. */
@Composable
actual fun systemReducedMotion(): Boolean = false

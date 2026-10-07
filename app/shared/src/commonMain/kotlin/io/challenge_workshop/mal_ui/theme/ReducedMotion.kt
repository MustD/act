package io.challenge_workshop.mal_ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable

/**
 * Whether the platform asks for less motion: Android's animator duration scale at 0, the browser's
 * `prefers-reduced-motion: reduce`, and never on desktop, which has no such setting to read.
 *
 * Kept current where the platform can change it while the app runs. [ActTheme] reads it once into
 * `Act.reducedMotion`; nothing else should call this.
 */
@Composable
expect fun systemReducedMotion(): Boolean

/**
 * [spec], or an instant jump when reduced motion is on.
 *
 * Every transition goes through this rather than testing `Act.reducedMotion` itself, so "has an
 * instant path" is one call to look for rather than one `if` to get right at each site.
 */
@Composable
fun <T> motion(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
    if (Act.reducedMotion) snap() else spec

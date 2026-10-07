package io.challenge_workshop.mal_ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Fades what follows in the chain to [disabledAlpha] while not [enabled]. Use this, not
 * `alpha(if (enabled) 1f else …)`.
 *
 * `Modifier.alpha(1f)` returns the modifier unchanged, so that expression adds a layer while disabled
 * and removes it once enabled. Removing a layer from the middle of a chain loses the draw modifiers
 * ahead of it (a `background`, a `border`) until something redraws the whole tree. That is how the
 * episode + lost its fill when an Anime Page finished loading, and got it back on a theme toggle.
 * Here the layer stays and only its alpha changes.
 */
fun Modifier.enabledAlpha(enabled: Boolean, disabledAlpha: Float): Modifier =
    graphicsLayer {
        alpha = if (enabled) 1f else disabledAlpha
        clip = true
    }

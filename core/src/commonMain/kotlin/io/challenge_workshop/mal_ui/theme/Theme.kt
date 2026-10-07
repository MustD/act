package io.challenge_workshop.mal_ui.theme

import kotlinx.serialization.Serializable

/**
 * Whether the app is drawn dark or light, or follows the device.
 *
 * Stored by name, so an unknown name from a later build reads as [System].
 */
@Serializable
enum class Theme {
    Dark,
    Light,
    System;

    /** What this Theme draws, given what the device asks for: [System] defers to [systemDark]. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        Dark -> true
        Light -> false
        System -> systemDark
    }
}

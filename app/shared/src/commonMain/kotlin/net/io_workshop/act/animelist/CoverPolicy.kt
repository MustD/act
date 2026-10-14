package net.io_workshop.act.animelist

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether an Adult Cover (`CONTEXT.md`) is drawn on this Target. Bound per platform in Koin:
 * Android withholds it, desktop and web draw it.
 */
fun interface CoverPolicy {
    /** True when a cover that [isAdultCover] says is adult must be replaced by the placeholder. */
    fun hidesAdultCovers(): Boolean

    companion object {
        val HideAdult: CoverPolicy = CoverPolicy { true }
        val ShowAll: CoverPolicy = CoverPolicy { false }
    }
}

/**
 * Provided once, by `App()`. The default hides, so a composition that forgot to provide one fails
 * closed rather than drawing an Adult Cover.
 */
val LocalCoverPolicy = staticCompositionLocalOf { CoverPolicy.HideAdult }

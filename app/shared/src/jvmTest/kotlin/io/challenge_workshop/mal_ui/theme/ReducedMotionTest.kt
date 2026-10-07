@file:OptIn(ExperimentalTestApi::class)

package io.challenge_workshop.mal_ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ReducedMotionTest {
    /** Renders a [motion]-driven float, flips its target, advances one frame only, and returns what it shows. */
    private fun valueOneFrameAfterRetarget(reduced: Boolean): Float {
        var target by mutableStateOf(0f)
        var shown = 0f
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent {
                ActTheme(dark = true, reducedMotion = reduced) {
                    shown = animateFloatAsState(target, motion(tween(1_000)), label = "probe").value
                }
            }
            mainClock.advanceTimeByFrame()
            target = 1f
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
        }
        return shown
    }

    @Test
    fun a_value_lands_without_the_clock_advancing_when_motion_is_reduced() {
        assertEquals(1f, valueOneFrameAfterRetarget(reduced = true))
    }

    @Test
    fun the_same_value_is_still_travelling_when_motion_is_not_reduced() {
        assertNotEquals(1f, valueOneFrameAfterRetarget(reduced = false))
    }

    @Test
    fun the_spinner_holds_its_first_frame_when_motion_is_reduced() {
        var glyph = ' '
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent { ActTheme(dark = true, reducedMotion = true) { glyph = rememberSpinnerGlyph() } }
            mainClock.advanceTimeBy(2_000)
        }
        assertEquals('⠋', glyph)
    }

    @Test
    fun the_spinner_advances_when_motion_is_not_reduced() {
        var glyph = ' '
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent { ActTheme(dark = true, reducedMotion = false) { glyph = rememberSpinnerGlyph() } }
            mainClock.advanceTimeBy(2_000)
        }
        assertNotEquals('⠋', glyph)
    }
}

package io.challenge_workshop.mal_ui.theme

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnimatorScaleTest {
    @Test
    fun only_a_scale_of_zero_is_reduced_motion() {
        assertTrue(animatorScaleIsReduced(0f))
        assertFalse(animatorScaleIsReduced(1f))
        assertFalse(animatorScaleIsReduced(0.5f))
    }
}

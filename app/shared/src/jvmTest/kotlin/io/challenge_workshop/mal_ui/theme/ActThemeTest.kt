@file:OptIn(ExperimentalTestApi::class)

package io.challenge_workshop.mal_ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ActThemeTest {
    private class Seen(val colors: ActColors, val scheme: ColorScheme, val shapes: Shapes, val typography: Typography, val sans: FontFamily?)

    private fun render(dark: Boolean, accent: ActAccent = ActAccent.Phosphor): Seen {
        var seen: Seen? = null
        runComposeUiTest {
            setContent {
                ActTheme(dark = dark, accent = accent) {
                    seen = Seen(Act.colors, MaterialTheme.colorScheme, MaterialTheme.shapes, MaterialTheme.typography, Act.type.screenTitle.fontFamily)
                }
            }
            waitForIdle()
        }
        return seen!!
    }

    @Test
    fun dark_theme_uses_the_handoff_tokens_and_phosphor() {
        val c = render(dark = true).colors
        assertEquals(Color(0xFF0D0E0C), c.bg)
        assertEquals(Color(0xFF8CEB7B), c.acc)
        assertEquals(Color(0xFFF2C057), c.pend)
        assertEquals(true, c.isDark)
    }

    @Test
    fun light_theme_uses_the_handoff_tokens_and_phosphor() {
        val c = render(dark = false).colors
        assertEquals(Color(0xFFF1F0E9), c.bg)
        assertEquals(Color(0xFF2E7B33), c.acc)
        assertEquals(Color(0xFFA86A12), c.pend)
    }

    @Test
    fun pending_never_matches_the_accent() {
        for (dark in listOf(true, false)) for (accent in ActAccent.entries) {
            val c = actColors(dark, accent)
            assertNotEquals(c.acc, c.pend, "$accent dark=$dark")
        }
    }

    @Test
    fun the_tokens_reach_stock_m3_components() {
        val seen = render(dark = true)
        assertEquals(seen.colors.acc, seen.scheme.primary)
        assertEquals(seen.colors.bg, seen.scheme.surface)
        assertEquals(seen.colors.sf, seen.scheme.surfaceContainer)
        assertEquals(seen.colors.ln, seen.scheme.outline)
        assertEquals(RoundedCornerShape(4.dp), seen.shapes.extraLarge)
        assertEquals(RoundedCornerShape(2.dp), seen.shapes.small)
    }

    @Test
    fun the_type_scale_reaches_m3_and_uses_the_bundled_fonts() {
        val seen = render(dark = true)
        assertEquals(30f, seen.typography.headlineLarge.fontSize.value)
        assertEquals(FontWeight.SemiBold, seen.typography.headlineLarge.fontWeight)
        assertNotEquals(null, seen.sans)
    }
}

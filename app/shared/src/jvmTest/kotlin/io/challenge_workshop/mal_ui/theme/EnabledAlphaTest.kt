@file:OptIn(ExperimentalTestApi::class)

package io.challenge_workshop.mal_ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class EnabledAlphaTest {
    @Test
    fun the_fill_ahead_of_it_survives_becoming_enabled() {
        runComposeUiTest {
            var enabled by mutableStateOf(false)
            setContent {
                Box(
                    Modifier.size(40.dp).clip(ActMedium).background(Color.Red)
                        .enabledAlpha(enabled, 0.4f).testTag("box"),
                )
            }
            waitForIdle()
            enabled = true
            waitForIdle()

            assertEquals(Color.Red, onNodeWithTag("box").captureToImage().toPixelMap()[20, 20])
        }
    }

    @Test
    fun what_follows_is_faded_only_while_disabled() {
        for (enabled in listOf(true, false)) {
            runComposeUiTest {
                setContent {
                    Box(Modifier.size(40.dp).background(Color.White).testTag("box")) {
                        Box(Modifier.fillMaxSize().enabledAlpha(enabled, 0.5f).background(Color.Black))
                    }
                }

                val centre = onNodeWithTag("box").captureToImage().toPixelMap()[20, 20]
                if (enabled) assertEquals(Color.Black, centre) else assertEquals(0.5f, centre.red, 0.01f)
            }
        }
    }
}

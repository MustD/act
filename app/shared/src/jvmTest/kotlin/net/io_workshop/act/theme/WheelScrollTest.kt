@file:OptIn(ExperimentalTestApi::class)

package net.io_workshop.act.theme

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WheelScrollTest {

    private class Scrolls {
        lateinit var row: ScrollState
        lateinit var page: ScrollState
    }

    private fun ComposeUiTest.rowInPage(scrolls: Scrolls) = setContent {
        scrolls.row = rememberScrollState()
        scrolls.page = rememberScrollState()
        Column(Modifier.height(200.dp).width(200.dp).verticalScroll(scrolls.page)) {
            Row(Modifier.testTag("row").width(200.dp).wheelHorizontalScroll(scrolls.row).horizontalScroll(scrolls.row)) {
                Box(Modifier.width(1000.dp).height(50.dp))
            }
            Box(Modifier.height(1000.dp))
        }
    }

    @Test
    fun the_vertical_wheel_over_a_row_scrolls_the_row_and_not_the_page() {
        runComposeUiTest {
            val s = Scrolls()
            rowInPage(s)

            onNodeWithTag("row").performMouseInput { scroll(1f) }
            waitForIdle()

            assertTrue(s.row.value > 0, "row did not move")
            assertEquals(0, s.page.value)
        }
    }

    @Test
    fun at_the_end_of_the_row_the_wheel_scrolls_the_page_instead() {
        runComposeUiTest {
            val s = Scrolls()
            rowInPage(s)
            runOnIdle { kotlinx.coroutines.runBlocking { s.row.scrollTo(s.row.maxValue) } }
            val rowAtEnd = s.row.value

            onNodeWithTag("row").performMouseInput { scroll(1f) }
            waitForIdle()

            assertEquals(rowAtEnd, s.row.value)
            assertTrue(s.page.value > 0, "page did not move")
        }
    }
}

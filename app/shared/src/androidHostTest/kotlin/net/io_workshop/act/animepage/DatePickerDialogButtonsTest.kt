package net.io_workshop.act.animepage

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import net.io_workshop.act.theme.ActTheme
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Android's `DatePickerDialog` wraps the whole `dismissButton` slot in one `Box`, where the skiko one
 * lays it straight into the button row — so two buttons put in that slot side by side on desktop and
 * web are drawn on top of each other here. Only this target runs androidx's layout.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class DatePickerDialogButtonsTest {

    @Test
    fun clear_cancel_and_ok_do_not_overlap() = runComposeUiTest {
        setContent {
            ActTheme(dark = true, reducedMotion = true) {
                DateRow("started", "2024-05-01", enabled = true, "value", "set", "clear", saving = false) {}
            }
        }

        onNodeWithTag("set").performClick()

        val clear = onNodeWithTag("clear").getUnclippedBoundsInRoot()
        val cancel = onNodeWithText("Cancel").getUnclippedBoundsInRoot()
        val ok = onNodeWithText("OK").getUnclippedBoundsInRoot()
        assertTrue(clear.right <= cancel.left, "Clear $clear must sit left of Cancel $cancel")
        assertTrue(cancel.right <= ok.left, "Cancel $cancel must sit left of OK $ok")
    }
}

@file:OptIn(ExperimentalTestApi::class)

package io.challenge_workshop.mal_ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.challenge_workshop.mal_ui.animepage.DateUpdate
import io.challenge_workshop.mal_ui.animepage.ListEntryUpdate
import io.challenge_workshop.mal_ui.animepage.LoggedSave
import io.challenge_workshop.mal_ui.animepage.SaveLog
import io.challenge_workshop.mal_ui.animepage.SaveOutcome
import io.challenge_workshop.mal_ui.animepage.logDescription
import io.challenge_workshop.mal_ui.auth.KEY_HINTS
import io.challenge_workshop.mal_ui.auth.LOG_BAR_HINTS_TAG
import io.challenge_workshop.mal_ui.auth.LOG_BAR_RESULT_TAG
import io.challenge_workshop.mal_ui.auth.LOG_BAR_TAG
import io.challenge_workshop.mal_ui.auth.LOG_BAR_TEXT_TAG
import io.challenge_workshop.mal_ui.animelist.WatchStatus
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** The log bar: how a save is worded, and what the bar shows for pending, accepted and refused. */
class LogBarTest {

    private fun saved(update: ListEntryUpdate, outcome: SaveOutcome) = LoggedSave("Dandadan", update, outcome)

    private fun draw(log: SaveLog, width: Int = 400, check: androidx.compose.ui.test.ComposeUiTest.() -> Unit) =
        runComposeUiTest {
            setContent { Box(Modifier.width(width.dp)) { ThemedSessionRoute(signedIn(saveLog = log), RecordedActions().actions) } }
            check()
        }

    @Test
    fun the_description_lists_only_the_fields_sent_in_a_fixed_order() {
        val update = ListEntryUpdate(
            watchStatus = WatchStatus.Completed,
            score = 7,
            episodesWatched = 12,
            startDate = DateUpdate.Set(LocalDate(2026, 1, 2)),
            finishDate = DateUpdate.Clear,
        )
        assertEquals(
            "> PATCH dandadan ep=12 score=7 status=completed start=2026-01-02 finish=-",
            logDescription(saved(update, SaveOutcome.Sent)),
        )
        assertEquals("> PATCH dandadan ep=8", logDescription(saved(ListEntryUpdate(episodesWatched = 8), SaveOutcome.Sent)))
    }

    @Test
    fun the_bar_is_28dp_and_empty_before_the_first_save() = draw(SaveLog()) {
        onNodeWithTag(LOG_BAR_TAG).assertHeightIsEqualTo(28.dp)
        onNodeWithTag(LOG_BAR_TEXT_TAG).assertTextEquals("")
        onAllNodesWithTag(LOG_BAR_RESULT_TAG).assertCountEquals(0)
    }

    @Test
    fun an_accepted_save_shows_the_duration() =
        draw(SaveLog(saved(ListEntryUpdate(episodesWatched = 8), SaveOutcome.Accepted(214)))) {
            onNodeWithTag(LOG_BAR_TEXT_TAG).assertTextEquals("> PATCH dandadan ep=8")
            onNodeWithTag(LOG_BAR_RESULT_TAG).assertTextEquals("✓ 214ms")
        }

    @Test
    fun a_refusal_shows_the_message() =
        draw(SaveLog(saved(ListEntryUpdate(score = 3), SaveOutcome.Refused("HTTP 400")))) {
            onNodeWithTag(LOG_BAR_RESULT_TAG).assertTextEquals("✗ HTTP 400")
        }

    @Test
    fun anything_pending_shows_a_spinner_frame_instead_of_the_last_result() =
        draw(SaveLog(saved(ListEntryUpdate(score = 3), SaveOutcome.Accepted(90)), pending = true)) {
            val shown = onNodeWithTag(LOG_BAR_RESULT_TAG).fetchSemanticsNode()
                .config[SemanticsProperties.Text].joinToString("") { it.text }
            assert(shown.length <= 3 && shown.none { it.isLetterOrDigit() }) { "expected a spinner glyph, got '$shown'" }
        }

    @Test
    fun key_hints_are_wide_only() {
        draw(SaveLog(), width = 600) { onAllNodesWithTag(LOG_BAR_HINTS_TAG).assertCountEquals(0) }
        draw(SaveLog(), width = 1000) { onNodeWithTag(LOG_BAR_HINTS_TAG).assertTextEquals(KEY_HINTS) }
    }
}

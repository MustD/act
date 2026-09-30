package io.challenge_workshop.mal_ui.animepage

import io.challenge_workshop.mal_ui.animelist.ListEntry
import io.challenge_workshop.mal_ui.animelist.WatchStatus
import io.challenge_workshop.mal_ui.animelist.WatchStatus.*
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class AutomaticRulesTest {
    private val today = LocalDate(2026, 9, 30)

    private fun entry(
        status: WatchStatus,
        watched: Int,
        start: String? = null,
        finish: String? = null,
    ) = ListEntry(
        status,
        score = 0,
        episodesWatched = watched,
        startDate = start,
        finishDate = finish,
        updatedAt = null
    )

    private class Case(
        val name: String,
        val before: ListEntry,
        val requested: ListEntry,
        val total: Int,
        val expected: ListEntry,
    )

    private fun cases() = listOf(
        Case("reaching the total completes and sets the finish date",
            entry(Watching, 11), entry(Watching, 12), 12, entry(Completed, 12, finish = "2026-09-30")),
        Case("reaching the total keeps a finish date already set",
            entry(Watching, 11, finish = "2025-01-01"), entry(Watching, 12, finish = "2025-01-01"), 12,
            entry(Completed, 12, finish = "2025-01-01")),
        Case("reaching the total from Dropped completes it",
            entry(Dropped, 5), entry(Dropped, 12), 12, entry(Completed, 12, finish = "2026-09-30")),
        Case("first episode of Plan to Watch starts it",
            entry(PlanToWatch, 0), entry(PlanToWatch, 1), 12, entry(Watching, 1, start = "2026-09-30")),
        Case("first episode keeps a start date already set",
            entry(PlanToWatch, 0, start = "2025-01-01"), entry(PlanToWatch, 1, start = "2025-01-01"), 12,
            entry(Watching, 1, start = "2025-01-01")),
        Case("first episode of a one-episode anime starts and completes it",
            entry(PlanToWatch, 0), entry(PlanToWatch, 1), 1,
            entry(Completed, 1, start = "2026-09-30", finish = "2026-09-30")),
        Case("first episode on a Dropped entry changes no status",
            entry(Dropped, 0), entry(Dropped, 1), 12, entry(Dropped, 1)),
        Case("a later episode on Plan to Watch is not a start",
            entry(PlanToWatch, 2), entry(PlanToWatch, 3), 12, entry(PlanToWatch, 3)),
        Case("choosing a status with the first episode is left alone",
            entry(PlanToWatch, 0), entry(Dropped, 1), 12, entry(Dropped, 1)),
        Case(
            "marking Completed fills in the episode count and the finish date",
            entry(Watching, 3), entry(Completed, 3), 12, entry(Completed, 12, finish = "2026-09-30")
        ),
        Case(
            "marking Completed keeps a finish date already set",
            entry(Watching, 3, finish = "2025-01-01"), entry(Completed, 3, finish = "2025-01-01"), 12,
            entry(Completed, 12, finish = "2025-01-01")
        ),
        Case(
            "marking Plan to Watch Completed fills no start date",
            entry(PlanToWatch, 0), entry(Completed, 0), 12, entry(Completed, 12, finish = "2026-09-30")
        ),
        Case("an entry already Completed is not refilled",
            entry(Completed, 12, finish = null), entry(Completed, 12, finish = null), 12,
            entry(Completed, 12, finish = null)),
        Case("clearing the finish date of a completed entry is not undone",
            entry(Completed, 12, finish = "2025-01-01"), entry(Completed, 12, finish = null), 12,
            entry(Completed, 12, finish = null)),
        Case("an unknown total applies nothing to progress",
            entry(Watching, 11), entry(Watching, 12), 0, entry(Watching, 12)),
        Case("an unknown total applies nothing to a first episode",
            entry(PlanToWatch, 0), entry(PlanToWatch, 1), 0, entry(PlanToWatch, 1)),
        Case("an unknown total applies nothing to Completed",
            entry(Watching, 3), entry(Completed, 3), 0, entry(Completed, 3)),
    )

    @Test
    fun each_rule() {
        for (c in cases()) {
            assertEquals(c.expected, applyAutomaticRules(c.before, c.requested, today, c.total), c.name)
        }
    }

    @Test
    fun adding_fills_in_what_the_chosen_status_implies() {
        fun added(status: WatchStatus, total: Int) = newListEntry(status, today, total)
        assertEquals(entry(PlanToWatch, 0), added(PlanToWatch, 12))
        assertEquals(entry(Watching, 0, start = "2026-09-30"), added(Watching, 12))
        assertEquals(entry(Completed, 12, finish = "2026-09-30"), added(Completed, 12))
        assertEquals(entry(WatchStatus.OnHold, 0), added(WatchStatus.OnHold, 12))
        assertEquals(entry(Dropped, 0), added(Dropped, 12))
    }

    @Test
    fun adding_as_completed_with_an_unknown_total_sets_only_the_finish_date() {
        assertEquals(entry(Completed, 0, finish = "2026-09-30"), newListEntry(Completed, today, 0))
    }
}

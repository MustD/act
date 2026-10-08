@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.challenge_workshop.mal_ui.animepage

import io.challenge_workshop.mal_ui.animelist.AiringStatus
import io.challenge_workshop.mal_ui.animelist.Anime
import io.challenge_workshop.mal_ui.animelist.ListEntry
import io.challenge_workshop.mal_ui.animelist.WatchStatus
import io.challenge_workshop.mal_ui.animelist.WatchStatus.Completed
import io.challenge_workshop.mal_ui.animelist.WatchStatus.Dropped
import io.challenge_workshop.mal_ui.animelist.WatchStatus.OnHold
import io.challenge_workshop.mal_ui.animelist.WatchStatus.PlanToWatch
import io.challenge_workshop.mal_ui.animelist.WatchStatus.Watching
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ListEntrySaves] over a scripted MAL: no HTTP, no pages. What is asserted is what goes out as a
 * PATCH, what is reported as confirmed, and the one state value the repository projects onto pages.
 */
class ListEntrySavesTest {
    private val today = LocalDate(2026, 9, 30)
    private val todayText = "2026-09-30"

    private fun entry(
        status: WatchStatus,
        watched: Int = 0,
        score: Int = 0,
        start: String? = null,
        finish: String? = null,
    ) = ListEntry(status, score, watched, start, finish, updatedAt = null)

    private fun anime(total: Int, id: Long = 1, title: String = "One") =
        Anime(id, title, picture = null, totalEpisodes = total, mediaType = "tv", airingStatus = AiringStatus.Unknown)

    /** A MAL that holds one entry, applies each PATCH to it, and can hold, clamp or refuse. */
    private class ScriptedMal(var held: ListEntry, private val clampEpisodesTo: Int? = null) {
        val sent = mutableListOf<ListEntryUpdate>()
        val confirmed = mutableListOf<Pair<Long, ListEntry>>()
        var gate: CompletableDeferred<Unit>? = null
        var refuseCall = 0
        var inFlight = 0
        var maxInFlight = 0

        suspend fun send(id: Long, update: ListEntryUpdate): ListEntry {
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            try {
                gate?.await()
            } finally {
                inFlight--
            }
            sent += update
            if (sent.size == refuseCall) throw IllegalStateException("MAL said no")
            held = held.copy(
                watchStatus = update.watchStatus ?: held.watchStatus,
                score = update.score ?: held.score,
                episodesWatched = (update.episodesWatched ?: held.episodesWatched)
                    .let { if (clampEpisodesTo != null) it.coerceAtMost(clampEpisodesTo) else it },
                startDate = update.startDate.applied(held.startDate),
                finishDate = update.finishDate.applied(held.finishDate),
            )
            return held
        }

        private fun DateUpdate?.applied(current: String?) = when (this) {
            null -> current
            DateUpdate.Clear -> null
            is DateUpdate.Set -> date.toString()
        }
    }

    private fun TestScope.saves(mal: ScriptedMal) = ListEntrySaves(
        scope = backgroundScope,
        send = mal::send,
        onConfirmed = { id, entry -> mal.confirmed += id to entry },
        today = { today },
    )

    private val ListEntrySaves.save get() = state.value.saves[1L]

    // region the automatic rules

    private class Case(
        val name: String,
        val start: ListEntry,
        val edit: ListEdit,
        val total: Int,
        /** Null when the edit must send nothing. */
        val expected: ListEntryUpdate?,
    )

    private fun cases() = listOf(
        Case("reaching the total completes and sets the finish date",
            entry(Watching, 11), ListEdit.SetEpisodes(12), 12,
            ListEntryUpdate(Completed, episodesWatched = 12, finishDate = DateUpdate.Set(today))),
        Case("reaching the total keeps a finish date already set",
            entry(Watching, 11, finish = "2025-01-01"), ListEdit.SetEpisodes(12), 12,
            ListEntryUpdate(Completed, episodesWatched = 12)),
        Case("reaching the total from Dropped completes it",
            entry(Dropped, 5), ListEdit.SetEpisodes(12), 12,
            ListEntryUpdate(Completed, episodesWatched = 12, finishDate = DateUpdate.Set(today))),
        Case("first episode of Plan to Watch starts it",
            entry(PlanToWatch, 0), ListEdit.SetEpisodes(1), 12,
            ListEntryUpdate(Watching, episodesWatched = 1, startDate = DateUpdate.Set(today))),
        Case("first episode keeps a start date already set",
            entry(PlanToWatch, 0, start = "2025-01-01"), ListEdit.SetEpisodes(1), 12,
            ListEntryUpdate(Watching, episodesWatched = 1)),
        Case("first episode of a one-episode anime starts and completes it",
            entry(PlanToWatch, 0), ListEdit.SetEpisodes(1), 1,
            ListEntryUpdate(
                Completed, episodesWatched = 1,
                startDate = DateUpdate.Set(today), finishDate = DateUpdate.Set(today),
            )),
        Case("first episode on a Dropped entry changes no status",
            entry(Dropped, 0), ListEdit.SetEpisodes(1), 12, ListEntryUpdate(episodesWatched = 1)),
        Case("a later episode on Plan to Watch is not a start",
            entry(PlanToWatch, 2), ListEdit.SetEpisodes(3), 12, ListEntryUpdate(episodesWatched = 3)),
        Case("marking Completed fills in the episode count and the finish date",
            entry(Watching, 3), ListEdit.SetWatchStatus(Completed), 12,
            ListEntryUpdate(Completed, episodesWatched = 12, finishDate = DateUpdate.Set(today))),
        Case("marking Completed keeps a finish date already set",
            entry(Watching, 3, finish = "2025-01-01"), ListEdit.SetWatchStatus(Completed), 12,
            ListEntryUpdate(Completed, episodesWatched = 12)),
        Case("marking Plan to Watch Completed fills no start date",
            entry(PlanToWatch, 0), ListEdit.SetWatchStatus(Completed), 12,
            ListEntryUpdate(Completed, episodesWatched = 12, finishDate = DateUpdate.Set(today))),
        Case("an entry already Completed is not refilled",
            entry(Completed, 12), ListEdit.SetEpisodes(12), 12, null),
        Case("clearing the finish date of a completed entry is not undone",
            entry(Completed, 12, finish = "2025-01-01"), ListEdit.SetFinishDate(null), 12,
            ListEntryUpdate(finishDate = DateUpdate.Clear)),
        Case("an unknown total applies nothing to progress",
            entry(Watching, 11), ListEdit.SetEpisodes(12), 0, ListEntryUpdate(episodesWatched = 12)),
        Case("an unknown total applies nothing to a first episode",
            entry(PlanToWatch, 0), ListEdit.SetEpisodes(1), 0, ListEntryUpdate(episodesWatched = 1)),
        Case("an unknown total applies nothing to Completed",
            entry(Watching, 3), ListEdit.SetWatchStatus(Completed), 0, ListEntryUpdate(Completed)),
    )

    @Test
    fun each_rule_decides_what_goes_out() = runTest {
        for (c in cases()) {
            val mal = ScriptedMal(c.start)
            val saves = saves(mal)

            saves.edit(anime(c.total), c.start, c.edit)
            runCurrent()

            assertEquals(listOfNotNull(c.expected), mal.sent, c.name)
        }
    }

    @Test
    fun choosing_a_status_after_the_first_episode_is_left_alone() = runTest {
        val start = entry(PlanToWatch, 0)
        val mal = ScriptedMal(start).apply { gate = CompletableDeferred() }
        val saves = saves(mal)

        saves.edit(anime(12), start, ListEdit.SetWatchStatus(Dropped))
        saves.edit(anime(12), start, ListEdit.SetEpisodes(1))
        mal.gate!!.complete(Unit)
        runCurrent()

        assertEquals(listOf(ListEntryUpdate(Dropped), ListEntryUpdate(episodesWatched = 1)), mal.sent)
    }

    @Test
    fun the_rules_see_the_pending_target_as_the_entry_before_the_edit() = runTest {
        val start = entry(PlanToWatch, 0)
        val mal = ScriptedMal(start).apply { gate = CompletableDeferred() }
        val saves = saves(mal)

        saves.edit(anime(12), start, ListEdit.SetEpisodes(1))
        // Shown is now Watching at 1, so a second episode is not a first episode again.
        saves.edit(anime(12), start, ListEdit.SetEpisodes(2))

        assertEquals(entry(Watching, 2, start = todayText), saves.save!!.target)
    }

    // endregion

    // region the loop

    @Test
    fun an_edit_is_pending_at_once_and_settles_with_mals_answer() = runTest {
        val start = entry(Watching, 3, score = 5)
        val mal = ScriptedMal(start).apply { gate = CompletableDeferred() }
        val saves = saves(mal)

        saves.edit(anime(26), start, ListEdit.SetScore(9))

        assertEquals(entry(Watching, 3, score = 9), saves.save!!.target)
        assertEquals(saves.save!!.target, saves.save!!.inFlight)
        mal.gate!!.complete(Unit)
        runCurrent()

        assertNull(saves.save, "a settled Save leaves nothing behind")
        assertEquals(listOf(ListEntryUpdate(score = 9)), mal.sent)
        assertEquals(listOf(1L to mal.held), mal.confirmed)
    }

    @Test
    fun three_rapid_plus_ones_send_two_patches_and_end_at_plus_three() = runTest {
        val start = entry(Watching, 3)
        val mal = ScriptedMal(start).apply { gate = CompletableDeferred() }
        val saves = saves(mal)

        repeat(3) { saves.edit(anime(26), start, ListEdit.AddEpisodes(1)) }
        assertEquals(6, saves.save!!.target!!.episodesWatched, "every tap counts on the target")
        mal.gate!!.complete(Unit)
        runCurrent()

        assertEquals(listOf(4, 6), mal.sent.map { it.episodesWatched }, "the first at once, the others as one")
        assertEquals(1, mal.maxInFlight)
    }

    @Test
    fun a_second_edit_of_another_field_rides_the_follow_up_and_repeats_nothing() = runTest {
        val start = entry(Watching, 3)
        val mal = ScriptedMal(start).apply { gate = CompletableDeferred() }
        val saves = saves(mal)

        saves.edit(anime(26), start, ListEdit.SetWatchStatus(OnHold))
        saves.edit(anime(26), start, ListEdit.SetScore(7))
        mal.gate!!.complete(Unit)
        runCurrent()

        assertEquals(listOf(ListEntryUpdate(OnHold), ListEntryUpdate(score = 7)), mal.sent)
    }

    @Test
    fun an_edit_that_changes_nothing_sends_nothing_and_leaves_the_state_alone() = runTest {
        val start = entry(Watching, 3, score = 5)
        val mal = ScriptedMal(start)
        val saves = saves(mal)
        val before = saves.state.value

        saves.edit(anime(26), start, ListEdit.SetScore(5))
        saves.edit(anime(26), start, ListEdit.AddEpisodes(0))
        runCurrent()

        assertTrue(mal.sent.isEmpty())
        assertEquals(before, saves.state.value)
    }

    @Test
    fun mals_own_answer_is_what_is_confirmed_even_when_it_differs_and_nothing_loops() = runTest {
        val start = entry(Watching, 3)
        val mal = ScriptedMal(start, clampEpisodesTo = 12)
        val saves = saves(mal)

        saves.edit(anime(26), start, ListEdit.SetEpisodes(20))
        runCurrent()

        assertEquals(1, mal.sent.size, "a clamp is not a difference to chase")
        assertEquals(12, mal.confirmed.single().second.episodesWatched)
        assertNull(saves.save)
    }

    @Test
    fun saves_of_different_anime_do_not_wait_for_each_other() = runTest {
        val start = entry(Watching, 3)
        val mal = ScriptedMal(start).apply { gate = CompletableDeferred() }
        val saves = saves(mal)

        saves.edit(anime(26, id = 1), start, ListEdit.SetScore(1))
        saves.edit(anime(26, id = 2), start, ListEdit.SetScore(2))

        assertEquals(2, mal.inFlight)
        mal.gate!!.complete(Unit)
        runCurrent()
    }

    // endregion

    // region what goes on the wire

    @Test
    fun dates_are_sent_as_dates_and_cleared_by_sending_the_field_empty() = runTest {
        val start = entry(Watching, 3, start = "2024")
        val mal = ScriptedMal(start)
        val saves = saves(mal)

        saves.edit(anime(26), start, ListEdit.SetFinishDate(LocalDate(2026, 9, 30)))
        runCurrent()
        saves.edit(anime(26), mal.held, ListEdit.SetStartDate(null))
        runCurrent()

        assertEquals(
            listOf(
                ListEntryUpdate(finishDate = DateUpdate.Set(today)),
                ListEntryUpdate(startDate = DateUpdate.Clear),
            ),
            mal.sent,
            "a partial `2024` MAL held and nobody touched is never a difference",
        )
    }

    @Test
    fun progress_is_held_to_zero_through_the_total_and_unlimited_when_the_total_is_unknown() = runTest {
        val start = entry(Watching, 3)
        val mal = ScriptedMal(start).apply { gate = CompletableDeferred() }
        val saves = saves(mal)

        saves.edit(anime(26), start, ListEdit.SetEpisodes(400))
        assertEquals(26, saves.save!!.target!!.episodesWatched, "held at once, before MAL answers")
        mal.gate!!.complete(Unit)
        runCurrent()
        saves.edit(anime(26), mal.held, ListEdit.AddEpisodes(-100))
        runCurrent()
        saves.edit(anime(0), mal.held, ListEdit.SetEpisodes(5000))
        runCurrent()

        assertEquals(listOf(26, 0, 5000), mal.sent.map { it.episodesWatched })
    }

    // endregion

    // region add

    @Test
    fun adding_sends_the_status_with_what_myanimelist_would_have_filled_in() = runTest {
        suspend fun added(status: WatchStatus, total: Int): ListEntryUpdate {
            val mal = ScriptedMal(entry(WatchStatus.Unknown))
            val saves = saves(mal)
            saves.add(anime(total), status)
            runCurrent()
            return mal.sent.single()
        }

        assertEquals(ListEntryUpdate(PlanToWatch), added(PlanToWatch, 12))
        assertEquals(ListEntryUpdate(Watching, startDate = DateUpdate.Set(today)), added(Watching, 12))
        assertEquals(
            ListEntryUpdate(Completed, episodesWatched = 12, finishDate = DateUpdate.Set(today)),
            added(Completed, 12),
        )
        assertEquals(ListEntryUpdate(OnHold), added(OnHold, 12))
        assertEquals(ListEntryUpdate(Dropped), added(Dropped, 12))
    }

    @Test
    fun adding_as_completed_with_an_unknown_total_sends_only_the_finish_date_and_status() = runTest {
        val mal = ScriptedMal(entry(WatchStatus.Unknown))
        val saves = saves(mal)

        saves.add(anime(0), Completed)
        runCurrent()

        assertEquals(ListEntryUpdate(Completed, finishDate = DateUpdate.Set(today)), mal.sent.single())
    }

    @Test
    fun an_add_is_ignored_while_one_is_pending_and_allowed_again_after_a_refusal() = runTest {
        val mal = ScriptedMal(entry(WatchStatus.Unknown)).apply { gate = CompletableDeferred(); refuseCall = 1 }
        val saves = saves(mal)

        saves.add(anime(12), Watching)
        saves.add(anime(12), Dropped)
        assertEquals(Watching, saves.save!!.target!!.watchStatus)
        mal.gate!!.complete(Unit)
        runCurrent()

        assertNotNull(saves.save!!.error)
        assertNull(saves.save!!.target)
        saves.add(anime(12), Dropped)
        runCurrent()
        assertEquals(2, mal.sent.size)
        assertNull(saves.save, "the retry started clean and was accepted")
    }

    // endregion

    // region refusals

    @Test
    fun a_refusal_drops_the_target_and_keeps_the_error_with_the_fields_it_carried() = runTest {
        val start = entry(Watching, 3, score = 5)
        val mal = ScriptedMal(start).apply { refuseCall = 1 }
        val saves = saves(mal)

        saves.edit(anime(26), start, ListEdit.SetScore(1))
        runCurrent()

        val save = saves.save!!
        assertNull(save.target)
        assertNull(save.inFlight)
        assertEquals("MAL said no", save.error)
        assertEquals(ListEntryUpdate(score = 1), save.errorFields)
        assertTrue(mal.confirmed.isEmpty(), "there was no answer to fall back to")
    }

    @Test
    fun a_refusal_re_sends_the_last_answer_of_that_save_so_the_page_falls_back_to_it() = runTest {
        val start = entry(Watching, 3, score = 5)
        val mal = ScriptedMal(start).apply { gate = CompletableDeferred(); refuseCall = 2 }
        val saves = saves(mal)

        saves.edit(anime(26), start, ListEdit.AddEpisodes(1))
        saves.edit(anime(26), start, ListEdit.SetScore(9))
        mal.gate!!.complete(Unit)
        runCurrent()

        val afterFirst = entry(Watching, 4, score = 5)
        assertEquals(listOf(1L to afterFirst, 1L to afterFirst), mal.confirmed, "answered once, then said again")
        assertEquals(ListEntryUpdate(score = 9), saves.save!!.errorFields)
    }

    @Test
    fun the_error_stays_until_the_next_edit_of_that_anime() = runTest {
        val start = entry(Watching, 3, score = 5)
        val mal = ScriptedMal(start).apply { refuseCall = 1 }
        val saves = saves(mal)
        saves.edit(anime(26), start, ListEdit.SetScore(1))
        runCurrent()

        saves.edit(anime(26, id = 2), start, ListEdit.SetScore(2))
        runCurrent()
        assertNotNull(saves.save!!.error, "another anime's edit does not clear it")

        mal.gate = CompletableDeferred()
        saves.edit(anime(26), start, ListEdit.SetScore(2))
        assertNull(saves.save!!.error, "trying again starts clean")
        assertEquals(ListEntryUpdate(), saves.save!!.errorFields)
        mal.gate!!.complete(Unit)
        runCurrent()
    }

    // endregion

    // region the Save Log

    @Test
    fun the_log_is_empty_before_the_first_save() = runTest {
        assertEquals(SaveLog(), saves(ScriptedMal(entry(Watching))).state.value.log)
    }

    @Test
    fun an_accepted_save_is_logged_with_its_title_fields_and_duration() = runTest {
        val start = entry(Watching, 3, score = 5)
        val saves = saves(ScriptedMal(start))

        saves.edit(anime(26, title = "Frieren"), start, ListEdit.SetScore(9))
        runCurrent()

        val log = saves.state.value.log
        assertEquals("Frieren", log.last!!.animeTitle)
        assertEquals(ListEntryUpdate(score = 9), log.last!!.update)
        assertTrue(assertIs<SaveOutcome.Accepted>(log.last!!.outcome).millis >= 0)
        assertTrue(!log.pending)
    }

    @Test
    fun a_save_in_flight_is_pending_and_logged_as_sent() = runTest {
        val start = entry(Watching, 3, score = 5)
        val mal = ScriptedMal(start).apply { gate = CompletableDeferred() }
        val saves = saves(mal)

        saves.edit(anime(26), start, ListEdit.SetScore(9))

        val log = saves.state.value.log
        assertTrue(log.pending)
        assertEquals(SaveOutcome.Sent, log.last!!.outcome)
        mal.gate!!.complete(Unit)
        runCurrent()
    }

    @Test
    fun a_refusal_is_logged_with_the_reason_and_nothing_is_pending() = runTest {
        val start = entry(Watching, 3, score = 5)
        val saves = saves(ScriptedMal(start).apply { refuseCall = 1 })

        saves.edit(anime(26), start, ListEdit.SetScore(1))
        runCurrent()

        val log = saves.state.value.log
        assertEquals(ListEntryUpdate(score = 1), log.last!!.update)
        assertEquals("MAL said no", assertIs<SaveOutcome.Refused>(log.last!!.outcome).message)
        assertTrue(!log.pending, "an error left to show is not a save still going")
    }

    @Test
    fun a_queued_follow_up_keeps_the_log_pending_between_the_two_sends() = runTest {
        val start = entry(Watching, 3, score = 5)
        val mal = ScriptedMal(start).apply { gate = CompletableDeferred() }
        val saves = saves(mal)
        saves.edit(anime(26), start, ListEdit.SetScore(9))
        saves.edit(anime(26), start, ListEdit.AddEpisodes(1))

        mal.gate!!.complete(Unit)
        runCurrent()

        val log = saves.state.value.log
        assertEquals(ListEntryUpdate(episodesWatched = 4), log.last!!.update)
        assertTrue(!log.pending)
    }

    // endregion

    // region onConfirmed

    @Test
    fun every_accepted_answer_goes_out_through_on_confirmed() = runTest {
        val start = entry(Watching, 3)
        val mal = ScriptedMal(start).apply { gate = CompletableDeferred() }
        val saves = saves(mal)
        saves.edit(anime(26), start, ListEdit.AddEpisodes(1))
        saves.edit(anime(26), start, ListEdit.AddEpisodes(1))

        mal.gate!!.complete(Unit)
        runCurrent()

        assertEquals(listOf(4, 5), mal.confirmed.map { it.second.episodesWatched })
    }

    // endregion
}

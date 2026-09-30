@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.challenge_workshop.mal_ui.animepage

import io.challenge_workshop.mal_ui.animelist.WatchStatus
import io.ktor.http.Parameters
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Editing the List Entry on an Anime Page: pending values, the target, one PATCH in flight, and the
 * way a failure goes back. Over the real repositories and a fake MAL that keeps a List Entry of its
 * own, so a PATCH really does change what the next one is compared against.
 */
class AnimePageEditTest {

    /** A MAL holding one List Entry (anime 1, 26 episodes, at 3), which the PATCHes really change. */
    private class Mal(total: Int = 26) {
        var entry = FakeListStatus(watchStatus = "watching", score = 5, watched = 3)
        val total = total
        var failNext = false

        /** The 1-based PATCH to refuse, wherever it falls. */
        var failCall = 0
        private var calls = 0

        /** Completed when the first PATCH reaches MAL. The engine runs off the test scheduler, so this is how a test waits for it. */
        val arrived = CompletableDeferred<Unit>()

        /** How many PATCHes are between arriving and being answered, and the most there ever were. */
        var inFlight = 0
        var maxInFlight = 0

        /** Each PATCH waits here first, so a test can hold one at MAL. */
        var gate: CompletableDeferred<Unit>? = null

        val respond: (Long, Parameters) -> ListStatusResponse = { _, form ->
            inFlight--
            calls++
            if (failNext || calls == failCall) {
                failNext = false
                ListStatusResponse.Failure()
            } else {
                entry = entry.applying(form, total)
                ListStatusResponse.Saved(entry)
            }
        }
        val hold: suspend (Long) -> Unit = {
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            arrived.complete(Unit)
            gate?.await()
        }
    }

    private suspend fun TestScope.loadedHarness(mal: Mal, pageTotal: Int = mal.total): Harness {
        val h = harness(
            animeDetails = { id ->
                AnimeDetailsResponse.Found(
                    FakeAnimeDetails(id, "One", numEpisodes = pageTotal, watchStatus = "watching", score = 5, watched = 3),
                )
            },
            updateListStatus = mal.respond,
            holdListStatusUpdate = mal.hold,
        )
        h.pages.open(h.entry(1))
        h.awaitLoaded()
        return h
    }

    private val AnimePage.shown get() = shownListStatus!!

    @Test
    fun nothing_is_editable_until_the_fetch_has_succeeded() = runTest {
        val gate = CompletableDeferred<Unit>()
        val mal = Mal()
        val h = harness(
            holdAnimeDetails = { gate.await() },
            updateListStatus = mal.respond,
            holdListStatusUpdate = mal.hold,
        )
        h.pages.open(h.entry(1))

        h.pages.edit(ListEdit.AddEpisodes(1))
        runCurrent()

        assertTrue(!h.history.current!!.canEdit)
        assertTrue(h.mal.listStatusPatches.isEmpty())
        assertNull(h.history.current!!.save.target)
        gate.complete(Unit)
        assertTrue(h.awaitLoaded().canEdit)
    }

    @Test
    fun a_failed_fetch_leaves_the_page_uneditable() = runTest {
        val h = harness(animeDetails = { AnimeDetailsResponse.Failure() })
        h.pages.open(h.entry(1))
        val failed = h.awaitPage { it.load is AnimePageLoad.Failed }

        h.pages.edit(ListEdit.SetScore(9))
        runCurrent()

        assertTrue(!failed.canEdit)
        assertTrue(h.mal.listStatusPatches.isEmpty())
    }

    @Test
    fun an_edit_shows_at_once_as_pending_and_sends_only_the_field_that_changed() = runTest {
        val mal = Mal().apply { gate = CompletableDeferred() }
        val h = loadedHarness(mal)

        h.pages.edit(ListEdit.SetScore(9))

        val pending = h.history.current!!
        assertEquals(9, pending.shown.score, "the requested value is shown straight away")
        assertEquals(5, pending.listStatus!!.score, "and MAL's confirmed one is still the old one")
        assertTrue(pending.isSaving)

        mal.gate!!.complete(Unit)
        val saved = h.awaitPage { !it.isSaving }
        assertEquals(9, saved.listStatus!!.score)
        assertNull(saved.save.error)
        val patch = h.mal.listStatusPatches.single()
        assertEquals(mapOf("score" to listOf("9")), patch.form.entries().associate { it.key to it.value })
        assertEquals("/v2/anime/1/my_list_status", patch.url.encodedPath)
    }

    @Test
    fun three_rapid_plus_ones_send_two_patches_and_end_at_plus_three() = runTest {
        val mal = Mal().apply { gate = CompletableDeferred() }
        val h = loadedHarness(mal)

        h.pages.edit(ListEdit.AddEpisodes(1))
        h.pages.edit(ListEdit.AddEpisodes(1))
        h.pages.edit(ListEdit.AddEpisodes(1))
        assertEquals(6, h.history.current!!.shown.episodesWatched, "every tap counts on the target")

        mal.gate!!.complete(Unit)
        val saved = h.awaitPage { !it.isSaving }

        assertEquals(6, saved.listStatus!!.episodesWatched)
        assertEquals(6, mal.entry.watched)
        val sent = h.mal.listStatusPatches.map { it.form["num_watched_episodes"] }
        assertEquals(listOf("4", "6"), sent, "the first tap went at once, the other two as one")
    }

    @Test
    fun there_is_never_more_than_one_patch_in_flight() = runTest {
        val mal = Mal().apply { gate = CompletableDeferred() }
        val h = loadedHarness(mal)

        repeat(5) { h.pages.edit(ListEdit.AddEpisodes(1)) }
        h.pages.edit(ListEdit.SetScore(2))
        mal.arrived.await()
        assertEquals(1, mal.inFlight)
        mal.gate!!.complete(Unit)
        h.awaitPage { !it.isSaving }

        assertEquals(1, mal.maxInFlight)
        assertEquals(8, mal.entry.watched)
        assertEquals(2, mal.entry.score)
    }

    @Test
    fun a_second_edit_of_another_field_rides_the_follow_up_and_repeats_nothing() = runTest {
        val mal = Mal().apply { gate = CompletableDeferred() }
        val h = loadedHarness(mal)

        h.pages.edit(ListEdit.SetWatchStatus(WatchStatus.OnHold))
        h.pages.edit(ListEdit.SetScore(7))
        mal.gate!!.complete(Unit)
        h.awaitPage { !it.isSaving }

        val forms = h.mal.listStatusPatches.map { p -> p.form.entries().associate { it.key to it.value } }
        assertEquals(
            listOf(mapOf("status" to listOf("on_hold")), mapOf("score" to listOf("7"))),
            forms,
        )
    }

    @Test
    fun a_refusal_goes_back_to_the_last_confirmed_values_with_an_error() = runTest {
        val mal = Mal().apply { gate = CompletableDeferred(); failCall = 2 }
        val h = loadedHarness(mal)
        // The first PATCH will be confirmed; the follow-up carrying the later edits is refused.
        h.pages.edit(ListEdit.AddEpisodes(1))
        h.pages.edit(ListEdit.AddEpisodes(1))
        h.pages.edit(ListEdit.SetScore(9))
        mal.gate!!.complete(Unit)

        val failed = h.awaitPage { !it.isSaving }

        assertEquals(4, failed.shown.episodesWatched, "back to what MAL last confirmed, not to the start")
        assertEquals(5, failed.shown.score)
        assertNotNull(failed.save.error)
        assertTrue(failed.save.error!!.isNotBlank())
    }

    @Test
    fun a_refused_edit_leaves_the_list_entry_alone_and_the_next_edit_clears_the_error() = runTest {
        val mal = Mal().apply { failNext = true }
        val h = loadedHarness(mal)

        h.pages.edit(ListEdit.SetScore(1))
        val failed = h.awaitPage { it.save.error != null }
        assertEquals(5, failed.shown.score)
        assertEquals(5, h.entry(1).score)

        h.pages.edit(ListEdit.SetScore(2))
        assertNull(h.history.current!!.save.error, "trying again starts clean")
        h.awaitPage { !it.isSaving }
        assertEquals(2, h.history.current!!.shown.score)
    }

    @Test
    fun mals_own_answer_is_the_confirmed_value_even_when_it_differs_and_nothing_loops() = runTest {
        val mal = Mal(total = 12)
        // The page thinks the total is 26; MAL knows better and clamps.
        val h = loadedHarness(mal, pageTotal = 26)

        h.pages.edit(ListEdit.SetEpisodes(20))
        val saved = h.awaitPage { !it.isSaving }

        assertEquals(12, saved.listStatus!!.episodesWatched)
        assertEquals(12, saved.shown.episodesWatched)
        assertEquals(1, h.mal.listStatusPatches.size, "a clamp is not a difference to chase")
    }

    @Test
    fun progress_is_limited_to_zero_through_the_total_and_unlimited_when_the_total_is_unknown() = runTest {
        val bounded = loadedHarness(Mal())
        bounded.pages.edit(ListEdit.SetEpisodes(400))
        assertEquals(26, bounded.history.current!!.shown.episodesWatched)
        bounded.awaitPage { !it.isSaving }
        bounded.pages.edit(ListEdit.AddEpisodes(-100))
        assertEquals(0, bounded.history.current!!.shown.episodesWatched)
        bounded.awaitPage { !it.isSaving }

        val unbounded = harness(
            animeDetails = { AnimeDetailsResponse.Found(FakeAnimeDetails(it, "One", numEpisodes = 0)) },
            updateListStatus = { _, form -> ListStatusResponse.Saved(FakeListStatus().applying(form)) },
        )
        unbounded.pages.open(unbounded.entry(1))
        unbounded.awaitLoaded()
        unbounded.pages.edit(ListEdit.SetEpisodes(5000))
        assertEquals(5000, unbounded.history.current!!.shown.episodesWatched)
    }

    @Test
    fun a_confirmed_save_updates_the_loaded_list_entry_in_place() = runTest {
        val mal = Mal()
        val h = loadedHarness(mal)
        val before = h.list.state.value
        val order = h.awaitEntries().map { it.animeId }

        h.pages.edit(ListEdit.SetWatchStatus(WatchStatus.Completed))
        h.awaitPage { !it.isSaving }
        runCurrent()

        val after = h.list.state.value
        val entries = (after.content as io.challenge_workshop.mal_ui.animelist.AnimeListContent.Entries).entries
        assertEquals(order, entries.map { it.animeId }, "no re-sorting")
        assertEquals(WatchStatus.Completed, entries.first { it.animeId == 1L }.watchStatus)
        assertEquals(before.revision, after.revision, "no reload, no re-filtering")
    }

    @Test
    fun dates_are_set_as_yyyy_mm_dd_and_cleared_by_sending_the_field_empty() = runTest {
        val mal = Mal().apply { entry = entry.copy(startDate = "2024") }
        val h = loadedHarness(mal)

        h.pages.edit(ListEdit.SetFinishDate(LocalDate(2026, 9, 30)))
        h.awaitPage { !it.isSaving }
        h.pages.edit(ListEdit.SetStartDate(null))
        val done = h.awaitPage { !it.isSaving }

        assertEquals("2026-09-30", h.mal.listStatusPatches[0].form["finish_date"])
        assertEquals(listOf(""), h.mal.listStatusPatches[1].form.getAll("start_date"))
        assertEquals("2026-09-30", done.listStatus!!.finishDate)
        assertNull(done.listStatus!!.startDate)
    }

    @Test
    fun an_edit_that_changes_nothing_sends_nothing() = runTest {
        val h = loadedHarness(Mal())

        h.pages.edit(ListEdit.SetScore(5))
        h.pages.edit(ListEdit.AddEpisodes(0))
        runCurrent()

        assertTrue(h.mal.listStatusPatches.isEmpty())
        assertTrue(!h.history.current!!.isSaving)
    }

    @Test
    fun leaving_a_page_mid_save_does_not_lose_the_save() = runTest {
        val mal = Mal().apply { gate = CompletableDeferred() }
        val h = loadedHarness(mal)
        h.pages.edit(ListEdit.AddEpisodes(1))
        h.pages.edit(ListEdit.AddEpisodes(1))
        runCurrent()

        h.pages.close()
        mal.gate!!.complete(Unit)
        runCurrent()
        h.list.state.first { s ->
            (s.content as? io.challenge_workshop.mal_ui.animelist.AnimeListContent.Entries)
                ?.entries?.first { it.animeId == 1L }?.episodesWatched == 5
        }

        assertEquals(5, mal.entry.watched, "the follow-up carrying the later tap went too")
        assertEquals(2, h.mal.listStatusPatches.size)
        assertTrue(!h.history.isOpen)
    }

    @Test
    fun coming_back_to_a_page_shows_the_save_that_is_still_in_flight() = runTest {
        val mal = Mal().apply { gate = CompletableDeferred() }
        val h = loadedHarness(mal)
        h.pages.edit(ListEdit.SetScore(9))
        runCurrent()

        h.pages.close()
        h.pages.open(h.entry(1))

        assertEquals(9, h.history.current!!.shown.score)
        assertTrue(h.history.current!!.isSaving)
        mal.gate!!.complete(Unit)
        h.awaitPage { !it.isSaving && it.load == AnimePageLoad.Loaded }
    }

    @Test
    fun logging_the_last_episode_sends_completed_and_todays_date_in_the_same_patch_and_shows_it_pending() = runTest {
        val mal = Mal(total = 4).apply { gate = CompletableDeferred() }
        val h = loadedHarness(mal)

        h.pages.edit(ListEdit.SetEpisodes(4))

        val pending = h.history.current!!
        assertEquals(WatchStatus.Completed, pending.shown.watchStatus)
        assertEquals("2026-09-30", pending.shown.finishDate)
        assertEquals(WatchStatus.Watching, pending.listStatus!!.watchStatus, "MAL has not confirmed it yet")
        mal.gate!!.complete(Unit)
        h.awaitPage { !it.isSaving }

        assertEquals(1, h.mal.listStatusPatches.size)
        val form = h.mal.listStatusPatches.single().form
        assertEquals("completed", form["status"])
        assertEquals("4", form["num_watched_episodes"])
        assertEquals("2026-09-30", form["finish_date"])
    }

    @Test
    fun what_a_rule_added_goes_back_with_the_edit_that_triggered_it() = runTest {
        val mal = Mal(total = 4).apply { failNext = true }
        val h = loadedHarness(mal)

        h.pages.edit(ListEdit.SetEpisodes(4))
        val back = h.awaitPage { !it.isSaving && it.save.error != null }

        assertEquals(WatchStatus.Watching, back.shown.watchStatus)
        assertEquals(3, back.shown.episodesWatched)
        assertNull(back.shown.finishDate)
    }
}

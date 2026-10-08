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
 * Editing the List Entry on an Anime Page: what pages add on top of `ListEntrySaves` (whose own
 * rules are in `ListEntrySavesTest`) — when an edit is accepted, what a page shows while one is
 * pending, a Save outliving its page, and the Session's end. Over the real repositories and a fake
 * MAL that keeps a List Entry of its own.
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
            val held = gate
            arrived.complete(Unit)
            held?.await()
        }
    }

    private suspend fun TestScope.loadedHarness(mal: Mal, pageTotal: Int = mal.total): Harness {
        val h = harness(
            animeDetails = { id ->
                AnimeDetailsResponse.Found(
                    FakeAnimeDetails(id, "One", numEpisodes = pageTotal, watchStatus = "watching", score = 5, watched = 3),
                )
            },
            updateListEntry = mal.respond,
            holdListEntryUpdate = mal.hold,
        )
        h.pages.open(h.entry(1))
        h.awaitLoaded()
        return h
    }

    private val AnimePage.shown get() = shownListEntry!!

    @Test
    fun nothing_is_editable_until_the_fetch_has_succeeded() = runTest {
        val gate = CompletableDeferred<Unit>()
        val mal = Mal()
        val h = harness(
            holdAnimeDetails = { gate.await() },
            updateListEntry = mal.respond,
            holdListEntryUpdate = mal.hold,
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
        assertEquals(5, pending.listEntry!!.score, "and MAL's confirmed one is still the old one")
        assertTrue(pending.isSaving)

        mal.gate!!.complete(Unit)
        val saved = h.awaitPage { !it.isSaving }
        assertEquals(9, saved.listEntry!!.score)
        assertNull(saved.save.error)
        val patch = h.mal.listStatusPatches.single()
        assertEquals(mapOf("score" to listOf("9")), patch.form.entries().associate { it.key to it.value })
        assertEquals("/v2/anime/1/my_list_status", patch.url.encodedPath)
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
        assertEquals(WatchStatus.Watching, pending.listEntry!!.watchStatus, "MAL has not confirmed it yet")
        val marked = pending.pendingChange
        assertNotNull(marked.watchStatus, "what the rule added is pending with the edit")
        assertNotNull(marked.episodesWatched)
        assertNotNull(marked.finishDate)
        assertNull(marked.score, "a field nobody touched is not marked")
        assertNull(marked.startDate)
        mal.gate!!.complete(Unit)
        assertTrue(h.awaitPage { !it.isSaving }.pendingChange.isEmpty)

        assertEquals(1, h.mal.listStatusPatches.size)
        val form = h.mal.listStatusPatches.single().form
        assertEquals("completed", form["status"])
        assertEquals("4", form["num_watched_episodes"])
        assertEquals("2026-09-30", form["finish_date"])
    }


    @Test
    fun a_refused_saves_error_is_still_there_when_the_page_is_reopened() = runTest {
        val mal = Mal().apply { failNext = true }
        val h = loadedHarness(mal)
        h.pages.edit(ListEdit.SetScore(1))
        h.awaitPage { it.save.error != null }

        h.pages.close()
        h.pages.open(h.entry(1))

        assertNotNull(h.history.current!!.save.error, "drawn with it at once")
        assertEquals(ListEntryUpdate(score = 1), h.history.current!!.save.errorFields)
        assertNotNull(h.awaitLoaded().save.error, "and the fetch does not take it away")
        h.pages.edit(ListEdit.SetScore(2))
        assertNull(h.history.current!!.save.error, "the next edit does")
        h.awaitPage { !it.isSaving }
    }

    @Test
    fun a_fetch_landing_mid_save_does_not_replace_the_shown_value() = runTest {
        val mal = Mal().apply { gate = CompletableDeferred() }
        val h = loadedHarness(mal)
        h.pages.edit(ListEdit.SetScore(9))

        h.pages.close()
        h.pages.open(h.entry(1))
        val reopened = h.awaitLoaded()

        assertEquals(9, reopened.shown.score, "the fetch predates the Save")
        mal.gate!!.complete(Unit)
        assertEquals(9, h.awaitPage { !it.isSaving }.listEntry!!.score)
    }

    @Test
    fun a_stale_fetch_then_a_refusal_falls_back_to_the_saves_last_answer() = runTest {
        val first = CompletableDeferred<Unit>()
        val mal = Mal().apply { gate = first; failCall = 2 }
        val h = loadedHarness(mal)
        h.pages.edit(ListEdit.AddEpisodes(1))
        h.pages.edit(ListEdit.SetScore(9))
        mal.arrived.await()
        val second = CompletableDeferred<Unit>()
        mal.gate = second
        first.complete(Unit)
        h.awaitPage { it.listEntry!!.episodesWatched == 4 }

        h.pages.close()
        h.pages.open(h.entry(1))
        assertEquals(3, h.awaitLoaded().listEntry!!.episodesWatched, "the fetch predates the first answer")
        second.complete(Unit)
        val failed = h.awaitPage { it.save.error != null }

        assertEquals(4, failed.listEntry!!.episodesWatched, "not the stale fetch's 3")
        assertEquals(5, failed.shown.score)
    }

    @Test
    fun the_log_outlives_a_closed_page_and_ends_with_the_session() = runTest {
        val mal = Mal().apply { gate = CompletableDeferred() }
        val h = loadedHarness(mal)
        h.pages.edit(ListEdit.SetScore(9))

        h.pages.close()
        assertTrue(h.pages.log.value.pending, "the bar is the only place the save is still visible")
        mal.gate!!.complete(Unit)
        h.pages.log.first { !it.pending && it.last != null }

        h.session.signOut()
        h.pages.log.first { it == SaveLog() }
        assertTrue(!h.history.isOpen)
    }
}

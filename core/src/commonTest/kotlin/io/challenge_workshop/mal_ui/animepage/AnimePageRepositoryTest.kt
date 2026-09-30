@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.challenge_workshop.mal_ui.animepage

import io.challenge_workshop.mal_ui.animelist.AnimeListContent
import io.challenge_workshop.mal_ui.animelist.AnimeListEntry
import io.challenge_workshop.mal_ui.animelist.AnimeListRepository
import io.challenge_workshop.mal_ui.animelist.AnimeListResponse
import io.challenge_workshop.mal_ui.animelist.AnimeListSortOrder
import io.challenge_workshop.mal_ui.animelist.AnimeListState
import io.challenge_workshop.mal_ui.animelist.AnimeListTail
import io.challenge_workshop.mal_ui.animelist.FakeEntry
import io.challenge_workshop.mal_ui.animelist.WatchStatus
import io.challenge_workshop.mal_ui.session.FakeClock
import io.challenge_workshop.mal_ui.session.FakeKeyValueStore
import io.challenge_workshop.mal_ui.session.FakeMal
import io.challenge_workshop.mal_ui.session.JsonTokenStore
import io.challenge_workshop.mal_ui.session.MalSessionRepository
import io.challenge_workshop.mal_ui.session.SessionState
import io.challenge_workshop.mal_ui.session.TEST_CONFIG
import io.challenge_workshop.mal_ui.session.TEST_USER
import io.challenge_workshop.mal_ui.session.VALID_TOKENS
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Anime Page history and its lifetime, over a **real** [MalSessionRepository] and
 * [AnimeListRepository] and the fake MAL behind them, for the reason `AnimeListRepositoryTest` is.
 * What is asserted is what a caller can see: the history, the list's entries, and the requests MAL
 * was asked.
 */
class AnimePageRepositoryTest {

    @Test
    fun opening_an_entry_draws_the_page_from_the_row_before_the_fetch_lands() = runTest {
        val gate = CompletableDeferred<Unit>()
        val h = harness(holdAnimeDetails = { gate.await() })

        h.pages.open(h.entry(1))

        val page = h.history.current!!
        assertEquals(1, page.animeId)
        assertEquals("One", page.title)
        assertEquals(AnimePageLoad.Loading, page.load)
        val status = page.listStatus!!
        assertEquals(WatchStatus.Watching, status.watchStatus)
        assertEquals(5, status.score)
        assertEquals(3, status.episodesWatched)
        assertNull(status.startDate, "the list row has no dates")
        assertNull(page.synopsis)
        gate.complete(Unit)
        h.awaitLoaded()
    }

    @Test
    fun the_fetch_fills_the_page_in_with_the_synopsis_and_the_fresh_status() = runTest {
        val h = harness(animeDetails = {
            AnimeDetailsResponse.Found(
                FakeAnimeDetails(it, "One", synopsis = "Fresh.", watchStatus = "completed", score = 9,
                    watched = 26, startDate = "2024-01-01", finishDate = "2024"),
            )
        })

        h.pages.open(h.entry(1))
        val page = h.awaitLoaded()

        assertEquals("Fresh.", page.synopsis)
        val status = page.listStatus!!
        assertEquals(WatchStatus.Completed, status.watchStatus)
        assertEquals(26, status.episodesWatched)
        assertEquals("2024-01-01", status.startDate)
        assertEquals("2024", status.finishDate)
        assertEquals("/v2/anime/1", h.mal.animeDetailsRequests.single().encodedPath)
    }

    @Test
    fun a_failed_fetch_shows_an_error_and_retry_asks_again() = runTest {
        var failing = true
        val h = harness(animeDetails = {
            if (failing) AnimeDetailsResponse.Failure() else AnimeDetailsResponse.Found(FakeAnimeDetails(it, "One"))
        })

        h.pages.open(h.entry(1))
        val failed = h.awaitPage { it.load is AnimePageLoad.Failed }
        assertTrue((failed.load as AnimePageLoad.Failed).message.isNotBlank())
        assertEquals("One", failed.title, "the page keeps what it opened with")

        failing = false
        h.pages.retry()
        assertEquals(AnimePageLoad.Loading, h.history.current!!.load)
        h.awaitLoaded()
        assertEquals(2, h.mal.animeDetailsRequests.size)
    }

    @Test
    fun retry_does_nothing_unless_the_fetch_failed() = runTest {
        val h = harness()
        h.pages.open(h.entry(1))
        h.awaitLoaded()

        h.pages.retry()
        runCurrent()

        assertEquals(1, h.mal.animeDetailsRequests.size)
    }

    @Test
    fun the_fetched_status_is_written_into_the_loaded_list_entry_in_place() = runTest {
        val h = harness(animeDetails = {
            AnimeDetailsResponse.Found(FakeAnimeDetails(it, "One", watchStatus = "on_hold", score = 7, watched = 10))
        })
        h.list.setWatchStatus(WatchStatus.Watching)
        h.awaitEntries()
        runCurrent()
        val filtered = h.list.state.first { it.watchStatus == WatchStatus.Watching && it.settled() }
        val order = (filtered.content as AnimeListContent.Entries).entries.map { it.animeId }

        h.pages.open(h.entry(1))
        h.awaitLoaded()
        runCurrent()

        val after = h.list.state.value
        val entries = (after.content as AnimeListContent.Entries).entries
        assertEquals(order, entries.map { it.animeId }, "position is unchanged")
        val updated = entries.first { it.animeId == 1L }
        assertEquals(WatchStatus.OnHold, updated.watchStatus, "not re-filtered: it stays under Watching")
        assertEquals(7, updated.score)
        assertEquals(10, updated.episodesWatched)
        assertEquals(filtered.revision, after.revision, "an edit in place is not a replacement")
        assertEquals(WatchStatus.Watching, after.watchStatus)
        // The other entries were not touched.
        assertEquals(entries.first { it.animeId == 2L }, filtered.entryOf(2))
    }

    @Test
    fun a_fetched_anime_that_is_not_loaded_in_the_list_changes_nothing() = runTest {
        val h = harness(listEntries = listOf(FakeEntry(1, "One")))
        val before = h.list.state.value

        h.list.applyListStatus(99, MyListStatus(WatchStatus.Dropped, 1, 1, null, null, null))

        assertEquals(before, h.list.state.value)
    }

    @Test
    fun opening_another_entry_starts_a_new_history_and_abandons_the_old_fetch() = runTest {
        val gate = CompletableDeferred<Unit>()
        val held = CompletableDeferred<Unit>()
        val abandoned = CompletableDeferred<Unit>()
        val h = harness(holdAnimeDetails = { id ->
            if (id == 1L) {
                held.complete(Unit)
                try {
                    gate.await()
                } catch (e: CancellationException) {
                    abandoned.complete(Unit)
                    throw e
                }
            }
        })
        h.pages.open(h.entry(1))
        held.await()

        h.pages.open(h.entry(2))
        abandoned.await()

        assertEquals(listOf(2L), h.history.pages.map { it.animeId })
        h.awaitLoaded()
        assertEquals(listOf(2L), h.history.pages.map { it.animeId })
    }

    @Test
    fun back_from_the_only_page_and_close_both_leave_nothing_open() = runTest {
        val h = harness()
        h.pages.open(h.entry(1))
        h.awaitLoaded()

        h.pages.back()
        assertFalse(h.history.isOpen)

        h.pages.open(h.entry(2))
        h.awaitLoaded()
        h.pages.close()
        assertFalse(h.history.isOpen)

        // Nothing to go back from is not an error.
        h.pages.back()
        h.pages.close()
        assertFalse(h.history.isOpen)
    }

    @Test
    fun closing_a_page_still_fetching_abandons_the_fetch() = runTest {
        val gate = CompletableDeferred<Unit>()
        val held = CompletableDeferred<Unit>()
        val abandoned = CompletableDeferred<Unit>()
        val h = harness(holdAnimeDetails = {
            held.complete(Unit)
            try {
                gate.await()
            } catch (e: CancellationException) {
                abandoned.complete(Unit)
                throw e
            }
        })
        h.pages.open(h.entry(1))
        // Genuinely at MAL, not merely claimed: a request cancelled before it got there proves nothing.
        held.await()

        h.pages.close()
        abandoned.await()
        gate.complete(Unit)
        runCurrent()

        assertFalse(h.history.isOpen, "the late answer must not reopen it")
    }

    @Test
    fun a_reload_or_a_filter_change_leaves_the_page_open() = runTest {
        val h = harness()
        h.pages.open(h.entry(1))
        h.awaitLoaded()

        h.list.reload()
        runCurrent()
        h.list.setWatchStatus(WatchStatus.Completed)
        h.list.setSortOrder(AnimeListSortOrder.Title)
        runCurrent()
        h.awaitEntries()

        assertEquals(listOf(1L), h.history.pages.map { it.animeId })
        assertEquals(AnimePageLoad.Loaded, h.history.current!!.load)
    }

    @Test
    fun the_session_ending_closes_the_page_and_abandons_its_fetch() = runTest {
        val gate = CompletableDeferred<Unit>()
        val held = CompletableDeferred<Unit>()
        val abandoned = CompletableDeferred<Unit>()
        val h = harness(holdAnimeDetails = {
            held.complete(Unit)
            try {
                gate.await()
            } catch (e: CancellationException) {
                abandoned.complete(Unit)
                throw e
            }
        })
        h.pages.open(h.entry(1))
        held.await()

        h.session.signOut()
        h.session.state.first { it is SessionState.SignedOut }
        abandoned.await()
        gate.complete(Unit)
        runCurrent()

        assertEquals(AnimePageHistory(), h.history)
    }

    @Test
    fun opening_outside_a_session_does_nothing() = runTest {
        val h = harness()
        val entry = h.entry(1)
        h.session.signOut()
        h.session.state.first { it is SessionState.SignedOut }
        runCurrent()

        h.pages.open(entry)
        runCurrent()

        assertEquals(AnimePageHistory(), h.history)
        assertTrue(h.mal.animeDetailsRequests.isEmpty())
    }
}

private fun AnimeListState.settled(): Boolean =
    content.let { it is AnimeListContent.Entries && !it.replacing && it.tail != AnimeListTail.LoadingMore }

private fun AnimeListState.entryOf(id: Long): AnimeListEntry =
    (content as AnimeListContent.Entries).entries.first { it.animeId == id }

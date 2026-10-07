package io.challenge_workshop.mal_ui.animepage

import io.challenge_workshop.mal_ui.session.SessionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import io.ktor.http.Parameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The log bar's value: what the save loop recorded about the last PATCH, and whether any save is pending. */
class SaveLogTest {

    private var entry = FakeListStatus(watchStatus = "watching", score = 5, watched = 3)
    private var gate: CompletableDeferred<Unit>? = null
    private var refuse = false

    private suspend fun TestScope.loaded(): Harness {
        val h = harness(
            animeDetails = { id ->
                AnimeDetailsResponse.Found(
                    FakeAnimeDetails(id, "One", numEpisodes = 26, watchStatus = "watching", score = 5, watched = 3),
                )
            },
            updateListEntry = { _: Long, form: Parameters ->
                if (refuse) ListStatusResponse.Failure() else {
                    entry = entry.applying(form, 26)
                    ListStatusResponse.Saved(entry)
                }
            },
            holdListEntryUpdate = { gate?.await() },
        )
        h.pages.open(h.entry(1))
        h.awaitLoaded()
        return h
    }

    @Test
    fun the_log_is_empty_before_the_first_save() = runTest {
        val h = loaded()
        assertEquals(SaveLog(), h.pages.log.value)
    }

    @Test
    fun an_accepted_save_is_recorded_with_its_title_fields_and_duration() = runTest {
        val h = loaded()

        h.pages.edit(ListEdit.SetScore(9))
        val log = h.pages.log.first { !it.pending && it.last != null }

        val last = log.last!!
        assertEquals("One", last.animeTitle)
        assertEquals(ListEntryUpdate(score = 9), last.update)
        val outcome = assertIs<SaveOutcome.Accepted>(last.outcome)
        assertTrue(outcome.millis >= 0)
    }

    @Test
    fun a_save_in_flight_is_pending_and_logged_as_sent() = runTest {
        gate = CompletableDeferred()
        val h = loaded()

        h.pages.edit(ListEdit.SetScore(9))

        val log = h.pages.log.value
        assertTrue(log.pending)
        assertEquals(SaveOutcome.Sent, log.last!!.outcome)
        gate!!.complete(Unit)
        h.pages.log.first { !it.pending }
    }

    @Test
    fun a_refusal_is_recorded_with_the_reason_and_nothing_is_pending() = runTest {
        refuse = true
        val h = loaded()

        h.pages.edit(ListEdit.SetScore(1))
        val log = h.pages.log.first { !it.pending && it.last != null }

        assertEquals(ListEntryUpdate(score = 1), log.last!!.update)
        val outcome = assertIs<SaveOutcome.Refused>(log.last!!.outcome)
        assertTrue(outcome.message.isNotBlank())
    }

    @Test
    fun a_save_still_running_when_the_page_is_closed_stays_pending() = runTest {
        gate = CompletableDeferred()
        val h = loaded()
        h.pages.edit(ListEdit.SetScore(9))

        h.pages.close()

        assertFalse(h.history.isOpen)
        assertTrue(h.pages.log.value.pending, "the bar is the only place the save is still visible")
        assertNotNull(h.pages.log.value.last)
        gate!!.complete(Unit)
        val done = h.pages.log.first { !it.pending }
        assertIs<SaveOutcome.Accepted>(done.last!!.outcome)
    }

    @Test
    fun a_queued_follow_up_keeps_the_log_pending_between_the_two_sends() = runTest {
        gate = CompletableDeferred()
        val h = loaded()
        h.pages.edit(ListEdit.SetScore(9))
        h.pages.edit(ListEdit.AddEpisodes(1))
        gate!!.complete(Unit)

        val done = h.pages.log.first { !it.pending }

        assertEquals(ListEntryUpdate(episodesWatched = 4), done.last!!.update)
    }

    @Test
    fun the_log_ends_with_the_session() = runTest {
        val h = loaded()
        h.pages.edit(ListEdit.SetScore(9))
        h.pages.log.first { !it.pending && it.last != null }

        h.session.signOut()
        h.pages.log.first { it == SaveLog() }

        assertNull(h.pages.log.value.last)
    }
}

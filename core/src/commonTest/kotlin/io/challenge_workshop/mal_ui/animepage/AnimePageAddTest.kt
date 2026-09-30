@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.challenge_workshop.mal_ui.animepage

import io.challenge_workshop.mal_ui.animelist.WatchStatus
import io.ktor.http.Parameters
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Adding an anime that is not on the list, from its Anime Page. */
class AnimePageAddTest {
    /** Anime 1 is on the list, anime 99 (12 episodes) is not. */
    private val notOnList = { id: Long ->
        AnimeDetailsResponse.Found(
            FakeAnimeDetails(id, "Anime $id", numEpisodes = 12, watchStatus = if (id == 99L) null else "watching"),
        )
    }

    private suspend fun Harness.openUnlisted(): AnimePage {
        pages.open(entry(1))
        awaitLoaded()
        pages.open(RelatedAnime(99, "Anime 99", null, "Sequel", onList = false))
        return awaitPage { it.animeId == 99L && it.load == AnimePageLoad.Loaded }
    }

    private fun saved(form: Parameters) =
        ListStatusResponse.Saved(FakeListStatus().applying(form, totalEpisodes = 12))

    private fun Parameters.asMap() = entries().associate { it.key to it.value }

    @Test
    fun adding_sends_the_status_with_its_extras_and_then_the_entry_can_be_edited() = runTest {
        val h = harness(animeDetails = notOnList, updateListEntry = { _, form -> saved(form) })
        assertNull(h.openUnlisted().listEntry)

        h.pages.add(WatchStatus.Completed)
        val page = h.awaitPage { !it.isSaving && it.listEntry != null }

        assertEquals(
            mapOf("status" to listOf("completed"), "num_watched_episodes" to listOf("12"), "finish_date" to listOf("2026-09-30")),
            h.mal.listStatusPatches.single().form.asMap(),
        )
        assertEquals(WatchStatus.Completed, page.listEntry!!.watchStatus)
        assertTrue(page.canEdit)
    }

    @Test
    fun plan_to_watch_sends_the_status_alone() = runTest {
        val h = harness(animeDetails = notOnList, updateListEntry = { _, form -> saved(form) })
        h.openUnlisted()

        h.pages.add(WatchStatus.PlanToWatch)
        h.awaitPage { it.listEntry != null }

        assertEquals(mapOf("status" to listOf("plan_to_watch")), h.mal.listStatusPatches.single().form.asMap())
    }

    @Test
    fun a_refused_add_goes_back_to_not_on_your_list_with_the_error() = runTest {
        val h = harness(animeDetails = notOnList)
        h.openUnlisted()

        h.pages.add(WatchStatus.Watching)
        val failed = h.awaitPage { !it.isSaving }

        assertNull(failed.listEntry)
        assertNull(failed.shownListEntry)
        assertNotNull(failed.save.error)
    }

    @Test
    fun an_add_does_not_change_the_anime_list() = runTest {
        val h = harness(animeDetails = notOnList, updateListEntry = { _, form -> saved(form) })
        h.openUnlisted()

        h.pages.add(WatchStatus.Watching)
        h.awaitPage { it.listEntry != null }

        assertEquals(listOf(1L, 2L, 3L), h.awaitEntries().map { it.animeId }, "no entry is inserted")
    }

    @Test
    fun adding_is_ignored_before_the_fetch_and_for_an_anime_already_on_the_list() = runTest {
        val h = harness(holdAnimeDetails = { kotlinx.coroutines.CompletableDeferred<Unit>().await() })
        h.pages.open(h.entry(1))
        h.pages.add(WatchStatus.Dropped)
        runCurrent()
        assertTrue(h.mal.listStatusPatches.isEmpty())

        val loaded = harness(updateListEntry = { _, form -> saved(form) })
        loaded.pages.open(loaded.entry(1))
        loaded.awaitLoaded()
        loaded.pages.add(WatchStatus.Dropped)
        runCurrent()
        assertTrue(loaded.mal.listStatusPatches.isEmpty())
    }
}

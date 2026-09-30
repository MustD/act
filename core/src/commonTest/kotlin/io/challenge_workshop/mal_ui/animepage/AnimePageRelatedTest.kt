@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.challenge_workshop.mal_ui.animepage

import io.challenge_workshop.mal_ui.animelist.WatchStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Related Anime, and the history they build. */
class AnimePageRelatedTest {

    private val related = listOf(
        FakeRelated(10, "Prequel", relation = "Prequel"),
        FakeRelated(11, "Sequel", relation = "Sequel", onListAs = "watching"),
    )

    private fun details(id: Long, related: List<FakeRelated> = emptyList()) = AnimeDetailsResponse.Found(
        // Only the anime the list holds (1, 2, 3) and the related one marked as listed (11) are on it.
        FakeAnimeDetails(
            id, if (id == 1L) "One" else "Anime $id", related = related,
            watchStatus = if (id in setOf(1L, 2L, 3L, 11L)) "watching" else null,
        ),
    )

    @Test
    fun the_fetch_brings_related_anime_in_mals_order_with_relation_and_on_list_mark() = runTest {
        val h = harness(animeDetails = { details(it, related) })

        h.pages.open(h.entry(1))
        val page = h.awaitLoaded()

        assertEquals(listOf(10L, 11L), page.related.map { it.animeId })
        assertEquals(listOf("Prequel", "Sequel"), page.related.map { it.relation })
        assertEquals(listOf(false, true), page.related.map { it.onList })
        assertEquals("Prequel", page.related[0].title)
        assertTrue(page.related[0].picture != null)
    }

    @Test
    fun a_related_anime_without_a_picture_still_parses() = runTest {
        val h = harness(animeDetails = { details(it, listOf(FakeRelated(10, "Bare", withPicture = false))) })
        h.pages.open(h.entry(1))
        assertNull(h.awaitLoaded().related.single().picture)
    }

    @Test
    fun a_page_with_no_related_anime_key_has_none() = runTest {
        val h = harness()
        h.pages.open(h.entry(1))
        assertEquals(emptyList(), h.awaitLoaded().related)
    }

    @Test
    fun opening_a_related_anime_draws_its_cover_and_title_before_its_fetch_lands() = runTest {
        val gate = CompletableDeferred<Unit>()
        val h = harness(
            animeDetails = { details(it, if (it == 1L) related else emptyList()) },
            holdAnimeDetails = { if (it == 10L) gate.await() },
        )
        h.pages.open(h.entry(1))
        val first = h.awaitLoaded()

        h.pages.open(first.related[0])

        val page = h.history.current!!
        assertEquals(listOf(1L, 10L), h.history.pages.map { it.animeId })
        assertEquals("Prequel", page.anime.title)
        assertEquals(first.related[0].picture, page.anime.picture)
        assertEquals(AnimePageLoad.Loading, page.load)
        assertNull(page.listEntry, "an anime not on the list has no List Entry to show")
        assertFalse(page.canEdit)
        gate.complete(Unit)
        val loaded = h.awaitLoaded()
        assertEquals("Anime 10", loaded.anime.title)
        assertNull(loaded.listEntry, "still not on the list: no List Entry fields")
        assertFalse(loaded.canEdit)
    }

    @Test
    fun a_related_anime_on_the_list_becomes_editable_once_its_fetch_lands() = runTest {
        val h = harness(animeDetails = { details(it, if (it == 1L) related else emptyList()) })
        h.pages.open(h.entry(1))
        h.pages.open(h.awaitLoaded().related[1])
        assertFalse(h.history.current!!.canEdit)

        val page = h.awaitLoaded()

        assertEquals(11L, page.animeId)
        assertEquals(WatchStatus.Watching, page.listEntry!!.watchStatus)
        assertTrue(page.canEdit)
    }

    @Test
    fun open_related_related_back_back_close() = runTest {
        val h = harness(animeDetails = { details(it, listOf(FakeRelated(it * 10, "N${it * 10}"))) })
        h.pages.open(h.entry(1))
        h.pages.open(h.awaitLoaded().related.single())
        h.pages.open(h.awaitPage { it.animeId == 10L && it.load == AnimePageLoad.Loaded }.related.single())
        h.awaitPage { it.animeId == 100L && it.load == AnimePageLoad.Loaded }
        assertEquals(listOf(1L, 10L, 100L), h.history.pages.map { it.animeId })

        h.pages.back()
        assertEquals(listOf(1L, 10L), h.history.pages.map { it.animeId })
        assertEquals(AnimePageLoad.Loaded, h.history.current!!.load, "going back lands on a loaded page")

        h.pages.back()
        assertEquals(listOf(1L), h.history.pages.map { it.animeId })

        h.pages.close()
        assertFalse(h.history.isOpen)
    }

    @Test
    fun close_closes_every_page_at_once() = runTest {
        val h = harness(animeDetails = { details(it, listOf(FakeRelated(it * 10, "N"))) })
        h.pages.open(h.entry(1))
        h.pages.open(h.awaitLoaded().related.single())
        h.awaitPage { it.animeId == 10L && it.load == AnimePageLoad.Loaded }

        h.pages.close()

        assertFalse(h.history.isOpen)
    }

    @Test
    fun opening_from_the_list_starts_a_new_history() = runTest {
        val h = harness(animeDetails = { details(it, listOf(FakeRelated(it * 10, "N"))) })
        h.pages.open(h.entry(1))
        h.pages.open(h.awaitLoaded().related.single())
        h.awaitPage { it.animeId == 10L && it.load == AnimePageLoad.Loaded }

        h.pages.open(h.entry(2))

        assertEquals(listOf(2L), h.history.pages.map { it.animeId })
    }

    @Test
    fun a_page_left_for_a_related_one_keeps_its_state_when_gone_back_to() = runTest {
        val h = harness(animeDetails = { details(it, if (it == 1L) related else emptyList()) })
        h.pages.open(h.entry(1))
        val first = h.awaitLoaded()
        h.pages.open(first.related[0])
        h.awaitPage { it.animeId == 10L && it.load == AnimePageLoad.Loaded }

        h.pages.back()

        assertEquals(first, h.history.current)
    }

    @Test
    fun going_back_abandons_the_fetch_of_the_page_left() = runTest {
        val gate = CompletableDeferred<Unit>()
        val abandoned = CompletableDeferred<Unit>()
        val held = CompletableDeferred<Unit>()
        val h = harness(
            animeDetails = { details(it, related) },
            holdAnimeDetails = {
                if (it == 10L) {
                    held.complete(Unit)
                    try {
                        gate.await()
                    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                        abandoned.complete(Unit)
                        throw e
                    }
                }
            },
        )
        h.pages.open(h.entry(1))
        h.pages.open(h.awaitLoaded().related[0])
        held.await()

        h.pages.back()
        abandoned.await()

        assertEquals(listOf(1L), h.history.pages.map { it.animeId })
    }

    @Test
    fun going_back_from_an_anime_open_twice_keeps_the_fetch_the_lower_copy_needs() = runTest {
        // A → B → A. One fetch fills both copies of A, so leaving the top one must not cancel it.
        var fetchesOfOne = 0
        val gate = CompletableDeferred<Unit>()
        val h = harness(
            animeDetails = {
                when {
                    it == 1L && ++fetchesOfOne == 2 -> AnimeDetailsResponse.Failure()
                    it == 1L -> details(1, listOf(FakeRelated(10, "Ten")))
                    else -> details(it, listOf(FakeRelated(1, "One")))
                }
            },
            holdAnimeDetails = { if (it == 1L && fetchesOfOne == 2) gate.await() },
        )
        h.pages.open(h.entry(1))
        h.pages.open(h.awaitLoaded().related.single())
        h.pages.open(h.awaitPage { it.animeId == 10L && it.load == AnimePageLoad.Loaded }.related.single())
        // The top A's fetch fails, and that failure is on both copies; its retry is then held.
        h.awaitPage { it.animeId == 1L && it.load is AnimePageLoad.Failed }
        h.pages.retry()
        assertEquals(AnimePageLoad.Loading, h.history.pages.first().load)

        h.pages.back()
        h.pages.back()
        gate.complete(Unit)

        val lower = h.awaitPage { it.load == AnimePageLoad.Loaded }
        assertEquals(listOf(1L), h.history.pages.map { it.animeId })
        assertEquals(1L, lower.animeId)
    }

    @Test
    fun opening_a_related_anime_never_closes_the_page_it_came_from_if_the_fetch_fails() = runTest {
        val h = harness(animeDetails = {
            if (it == 10L) AnimeDetailsResponse.Failure() else details(it, related)
        })
        h.pages.open(h.entry(1))
        h.pages.open(h.awaitLoaded().related[0])

        h.awaitPage { it.load is AnimePageLoad.Failed }

        assertEquals(listOf(1L, 10L), h.history.pages.map { it.animeId })
        assertEquals("Prequel", h.history.current!!.anime.title)
    }
}

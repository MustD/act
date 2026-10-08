package io.challenge_workshop.mal_ui.animepage

import io.challenge_workshop.mal_ui.animelist.AiringStatus
import io.challenge_workshop.mal_ui.animelist.Anime
import io.challenge_workshop.mal_ui.animelist.ListEntry
import io.challenge_workshop.mal_ui.animelist.WatchStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [AnimePage.wouldChange]: what the stepper and the `+` key read instead of restating the episode bound. */
class AnimePageWouldChangeTest {
    private fun page(total: Int, watched: Int, target: Int? = null): AnimePage {
        fun entry(n: Int) = ListEntry(WatchStatus.Watching, 0, n, null, null, updatedAt = null)
        return AnimePage(
            anime = Anime(1, "One", picture = null, totalEpisodes = total, mediaType = "tv", airingStatus = AiringStatus.Unknown),
            listEntry = entry(watched),
            synopsis = null,
            load = AnimePageLoad.Loaded,
            save = PageSave(target = target?.let(::entry)),
        )
    }

    @Test
    fun one_more_episode_changes_nothing_at_the_total() {
        assertFalse(page(total = 12, watched = 12).wouldChange(ListEdit.AddEpisodes(1)))
        assertTrue(page(total = 12, watched = 11).wouldChange(ListEdit.AddEpisodes(1)))
    }

    @Test
    fun one_fewer_episode_changes_nothing_at_zero() {
        assertFalse(page(total = 12, watched = 0).wouldChange(ListEdit.AddEpisodes(-1)))
    }

    @Test
    fun an_unknown_total_has_no_upper_bound() {
        assertTrue(page(total = 0, watched = 500).wouldChange(ListEdit.AddEpisodes(1)))
    }

    @Test
    fun it_reads_the_pending_target_not_the_confirmed_entry() {
        assertFalse(page(total = 12, watched = 3, target = 12).wouldChange(ListEdit.AddEpisodes(1)))
    }

    @Test
    fun a_page_without_an_entry_changes_nothing() {
        assertFalse(page(12, 3).copy(listEntry = null).wouldChange(ListEdit.AddEpisodes(1)))
    }
}

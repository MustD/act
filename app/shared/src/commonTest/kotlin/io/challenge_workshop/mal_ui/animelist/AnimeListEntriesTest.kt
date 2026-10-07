package io.challenge_workshop.mal_ui.animelist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The pure half of a List Entry's rendering: which cover-art URL is asked for, and what the four
 * facts beside it say.
 *
 * The composables themselves are deliberately not a test seam — the spec says so, and a screenshot
 * of a card proves nothing a reader could not see. What *is* worth pinning is here: MAL omits fields
 * it has no value for and adds values this build has never heard of, and every one of those cases is
 * a string this code has to produce anyway.
 *
 * In `commonTest`, so `./gradlew :app:shared:testAndroidHostTest`, `:jvmTest` and
 * `:wasmJsTest` each run it — the labels are common code and a divergence would be a per-Target one.
 */
class AnimeListEntriesTest {

    @Test
    fun a_card_asks_for_the_large_cover_and_a_row_for_the_medium_one() {
        val entry = entry(picture = AnimePicture(medium = "https://cdn/m.jpg", large = "https://cdn/l.jpg"))

        assertEquals("https://cdn/l.jpg", entry.coverUrl(preferLarge = true))
        assertEquals("https://cdn/m.jpg", entry.coverUrl(preferLarge = false))
    }

    @Test
    fun either_size_stands_in_for_the_other_when_mal_sends_only_one() {
        val mediumOnly = entry(picture = AnimePicture(medium = "https://cdn/m.jpg"))
        val largeOnly = entry(picture = AnimePicture(large = "https://cdn/l.jpg"))

        assertEquals("https://cdn/m.jpg", mediumOnly.coverUrl(preferLarge = true))
        assertEquals("https://cdn/l.jpg", largeOnly.coverUrl(preferLarge = false))
    }

    /**
     * The placeholder case, decided here rather than in a composable: no URL means no `AsyncImage`
     * is built at all, and what is on screen is the placeholder that was drawn underneath it. MAL
     * omits `main_picture` entirely for an entry whose art it has none of, and has been seen to send
     * an empty string, so both have to arrive as the same nothing.
     */
    @Test
    fun an_entry_with_no_usable_picture_asks_for_no_url_at_all() {
        assertNull(entry(picture = null).coverUrl(preferLarge = true))
        assertNull(entry(picture = AnimePicture()).coverUrl(preferLarge = true))
        assertNull(entry(picture = AnimePicture(medium = "", large = "")).coverUrl(preferLarge = true))
    }

    @Test
    fun a_row_shows_watched_over_total() {
        assertEquals("7/12", entry(episodesWatched = 7, totalEpisodes = 12).rowProgress())
    }

    /** MAL sends `0` for a run whose length is not yet announced, and "3/0" reads as a bug. */
    @Test
    fun an_unknown_episode_total_shows_as_a_question_mark() {
        assertEquals("3/?", entry(episodesWatched = 3, totalEpisodes = 0).rowProgress())
        assertEquals("/?", entry(totalEpisodes = 0).totalLabel())
        assertEquals("/24", entry(totalEpisodes = 24).totalLabel())
    }

    /** MAL's scale starts at 1, so `0` is "not scored" and not a score of zero. */
    @Test
    fun a_score_of_zero_is_unscored() {
        assertEquals("sc 8", entry(score = 8).scoreTag())
        assertEquals("unscored", entry(score = 0).scoreTag())
    }

    @Test
    fun the_card_meta_line_is_type_airing_and_score() {
        val entry = entry(mediaType = "tv", airingStatus = AiringStatus.FinishedAiring, score = 8)

        assertEquals("TV · Finished · sc 8", entry.metaLine())
        assertEquals("TV · Finished · unscored", entry.copy(score = 0).metaLine())
    }

    /**
     * The two "MAL did not say" cases close up rather than leaving a gap between two separators.
     * Both are reachable: `media_type` is an omitted field, and [AiringStatus.Unknown] is where a
     * status this build has never seen lands.
     */
    @Test
    fun facts_mal_did_not_send_are_dropped_from_the_meta_line() {
        val entry = entry(mediaType = null, airingStatus = AiringStatus.Unknown, score = 0)

        assertEquals("unscored", entry.metaLine())
    }

    @Test
    fun the_detail_label_is_type_and_airing_without_the_score() {
        assertEquals("TV · Finished", entry().detailLabel())
    }

    /** `ls -l` columns: zero-padded to three digits so they line up, `???` for an unknown total. */
    @Test
    fun the_table_pads_episodes_and_scores_so_columns_line_up() {
        assertEquals("019/028", entry(episodesWatched = 19, totalEpisodes = 28).tableEpisodes())
        assertEquals("003/???", entry(episodesWatched = 3, totalEpisodes = 0).tableEpisodes())
        assertEquals("08", entry(score = 8).tableScore())
        assertEquals("10", entry(score = 10).tableScore())
        assertEquals("--", entry(score = 0).tableScore())
    }

    @Test
    fun mals_media_types_are_spelled_the_way_a_person_says_them() {
        assertEquals("TV", entry(mediaType = "tv").mediaTypeLabel())
        assertEquals("OVA", entry(mediaType = "ova").mediaTypeLabel())
        assertEquals("Movie", entry(mediaType = "movie").mediaTypeLabel())
        assertEquals(null, entry(mediaType = "unknown").mediaTypeLabel())
    }

    /**
     * `media_type` is left a string in `:core` because MAL adds them, so a type this build has never
     * heard of has to read as something rather than vanish.
     */
    @Test
    fun a_media_type_this_build_has_never_seen_is_tidied_rather_than_dropped() {
        assertEquals("Music Video", entry(mediaType = "music_video").mediaTypeLabel())
    }

    private fun entry(
        title: String = "Cowboy Bebop",
        picture: AnimePicture? = null,
        totalEpisodes: Int = 26,
        mediaType: String? = "tv",
        airingStatus: AiringStatus = AiringStatus.FinishedAiring,
        watchStatus: WatchStatus = WatchStatus.Watching,
        score: Int = 8,
        episodesWatched: Int = 3,
    ): AnimeListEntry = AnimeListEntry(
        animeId = 1,
        title = title,
        picture = picture,
        totalEpisodes = totalEpisodes,
        mediaType = mediaType,
        airingStatus = airingStatus,
        watchStatus = watchStatus,
        score = score,
        episodesWatched = episodesWatched,
        updatedAt = null,
    )
}

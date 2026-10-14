package net.io_workshop.act.animelist

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdultCoverTest {
    @Test
    fun a_safe_rated_white_anime_is_not_an_adult_cover() {
        assertFalse(isAdultCover(rating = "pg_13", nsfw = "white"))
    }

    @Test
    fun r_plus_key_art_stays_visible() {
        assertFalse(isAdultCover(rating = "r+", nsfw = "white"))
    }

    @Test
    fun rx_is_an_adult_cover_even_when_marked_white() {
        assertTrue(isAdultCover(rating = "rx", nsfw = "white"))
    }

    @Test
    fun anything_but_white_is_an_adult_cover() {
        assertTrue(isAdultCover(rating = "pg_13", nsfw = "gray"))
        assertTrue(isAdultCover(rating = "pg_13", nsfw = "black"))
        assertTrue(isAdultCover(rating = "pg_13", nsfw = "something_new"))
    }

    @Test
    fun a_missing_mark_fails_closed() {
        assertTrue(isAdultCover(rating = null, nsfw = "white"))
        assertTrue(isAdultCover(rating = "pg_13", nsfw = null))
        assertTrue(isAdultCover(rating = null, nsfw = null))
    }
}

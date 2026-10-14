package net.io_workshop.act

import net.io_workshop.act.animelist.WatchStatus
import net.io_workshop.act.animepage.animePagePath
import kotlin.test.Test
import kotlin.test.assertEquals

class AnimePagePathTest {
    @Test
    fun the_path_is_the_wire_key_and_a_slug_of_the_title() {
        assertEquals("~/watching/sousou-no-frieren", animePagePath(WatchStatus.Watching, "Sousou no Frieren"))
        assertEquals("~/on_hold/re-zero-kara-hajimeru", animePagePath(WatchStatus.OnHold, "Re:Zero -- kara Hajimeru!"))
    }

    @Test
    fun an_anime_not_on_the_list_has_no_status_to_show() {
        assertEquals("~/anime/cowboy-bebop", animePagePath(null, "Cowboy Bebop"))
        assertEquals("~/anime/cowboy-bebop", animePagePath(WatchStatus.Unknown, "Cowboy Bebop"))
    }

    @Test
    fun a_title_with_nothing_to_slug_leaves_the_directory_alone() {
        assertEquals("~/watching", animePagePath(WatchStatus.Watching, "!!!"))
    }
}

package io.challenge_workshop.mal_ui.animelist

import kotlin.test.Test
import kotlin.test.assertEquals

/** The words and the cycles of the Anime List chrome, as data: the drawing of them is `SignedInScreenTest`'s. */
class AnimeListChromeTest {

    @Test
    fun the_tabs_carry_mals_wire_keys_and_all() {
        assertEquals(
            listOf("watching", "completed", "on_hold", "dropped", "plan_to_watch", "all"),
            ANIME_LIST_FILTERS.map { it.tabKey() },
        )
    }

    @Test
    fun the_prompt_lists_the_current_slice_and_lists_everything_for_all() {
        assertEquals("mustd@mal:~$ ls watching/", promptText("mustd", WatchStatus.Watching))
        assertEquals("mustd@mal:~$ ls on_hold/", promptText("mustd", WatchStatus.OnHold))
        assertEquals("mustd@mal:~$ ls", promptText("mustd", null))
    }

    @Test
    fun the_prompt_falls_back_to_a_name_when_the_profile_has_not_loaded() {
        assertEquals("user@mal:~$ ls", promptText(null, null))
    }

    @Test
    fun the_sort_row_is_the_lowercased_label() {
        assertEquals("sort: last updated (newest first)", AnimeListSortOrder.LastUpdated.sortRowText())
        assertEquals("sort: title (a–z)", AnimeListSortOrder.Title.sortRowText())
    }

    @Test
    fun tapping_sort_cycles_through_all_four_and_wraps() {
        val orders = AnimeListSortOrder.entries
        var order = orders.first()
        val seen = buildList { repeat(orders.size) { add(order); order = order.next() } }
        assertEquals(orders, seen)
        assertEquals(orders.first(), order)
    }

    @Test
    fun the_layout_button_cycles_through_every_layout_and_wraps() {
        val layouts = AnimeListLayout.entries
        var layout = layouts.first()
        val seen = buildList { repeat(layouts.size) { add(layout); layout = layout.next() } }
        assertEquals(layouts, seen)
        assertEquals(layouts.first(), layout)
    }
}

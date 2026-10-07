package io.challenge_workshop.mal_ui.animelist

/** The two Layouts, in the order they are drawn — the default first. */
val ANIME_LIST_LAYOUTS: List<AnimeListLayout> = AnimeListLayout.entries.toList()

/**
 * What each Layout is called on screen.
 *
 * Here rather than in `:core` for the reason [AnimeListSortOrder] gives — `:server` depends on that
 * tier and has no business carrying display copy. The prompt row's button shows an icon instead.
 */
fun AnimeListLayout.layoutLabel(): String = when (this) {
    AnimeListLayout.Cards -> "Cards"
    AnimeListLayout.List -> "List"
}

/** The Layout the button moves to: the next one, wrapping to the first. */
fun AnimeListLayout.next(): AnimeListLayout = ANIME_LIST_LAYOUTS[(ordinal + 1) % ANIME_LIST_LAYOUTS.size]

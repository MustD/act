package io.challenge_workshop.mal_ui.animepage

/**
 * The scores a person can give, best first: MAL's own labels for 10 down to 1, then 0, which MAL
 * calls "no score".
 *
 * Display copy, so here and not in `:core`, for the reason `AnimeListSortOrder`'s labels are.
 */
val SCORE_CHOICES: List<Int> = (10 downTo 0).toList()

fun scoreLabel(score: Int): String = when (score) {
    10 -> "Masterpiece"
    9 -> "Great"
    8 -> "Very Good"
    7 -> "Good"
    6 -> "Fine"
    5 -> "Average"
    4 -> "Bad"
    3 -> "Very Bad"
    2 -> "Horrible"
    1 -> "Appalling"
    else -> "No score"
}

/** What a score reads as in a picker or on the page: "8 – Very Good", or "No score". */
fun scoreText(score: Int): String = if (score in 1..10) "$score – ${scoreLabel(score)}" else scoreLabel(0)

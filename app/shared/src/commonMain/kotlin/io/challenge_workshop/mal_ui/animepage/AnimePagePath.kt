package io.challenge_workshop.mal_ui.animepage

import io.challenge_workshop.mal_ui.animelist.WatchStatus

/**
 * The page's name in its top bar, as a path: `~/watching/sousou-no-frieren`. The directory is the
 * Watch Status's wire key, or `anime` for one the user has no status for; the file is the title
 * lowercased with each run of anything but letters and digits as one `-`.
 */
fun animePagePath(status: WatchStatus?, title: String): String {
    val directory = status?.wireValue ?: "anime"
    val slug = buildString {
        for (c in title.lowercase()) {
            if (c.isLetterOrDigit()) append(c) else if (isNotEmpty() && last() != '-') append('-')
        }
    }.trimEnd('-')
    return if (slug.isEmpty()) "~/$directory" else "~/$directory/$slug"
}

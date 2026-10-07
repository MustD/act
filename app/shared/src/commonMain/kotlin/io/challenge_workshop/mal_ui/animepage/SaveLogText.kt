package io.challenge_workshop.mal_ui.animepage

/**
 * What the log bar's left side says about [save]: `> PATCH dandadan ep=8 score=7`. Only the fields
 * that went on the wire, in a fixed order; a cleared date reads `-`.
 */
fun logDescription(save: LoggedSave): String = buildString {
    append("> PATCH ").append(titleSlug(save.animeTitle).ifEmpty { "anime" })
    val u = save.update
    u.episodesWatched?.let { append(" ep=").append(it) }
    u.score?.let { append(" score=").append(it) }
    u.watchStatus?.let { s -> s.wireValue?.let { append(" status=").append(it) } }
    u.startDate?.let { append(" start=").append(it.shown()) }
    u.finishDate?.let { append(" finish=").append(it.shown()) }
}

private fun DateUpdate.shown(): String = when (this) {
    DateUpdate.Clear -> "-"
    is DateUpdate.Set -> date.toString()
}

/** The right side of an answered save: `✓ 214ms`, or `✗ <message>`; null while it is still out. */
fun logResult(outcome: SaveOutcome): String? = when (outcome) {
    SaveOutcome.Sent -> null
    is SaveOutcome.Accepted -> "✓ ${outcome.millis}ms"
    is SaveOutcome.Refused -> "✗ ${outcome.message}"
}

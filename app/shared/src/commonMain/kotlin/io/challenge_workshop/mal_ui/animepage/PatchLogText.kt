package io.challenge_workshop.mal_ui.animepage

/**
 * What the log bar's left side says about [patch]: `> PATCH dandadan ep=8 score=7`. Only the fields
 * that went on the wire, in a fixed order; a cleared date reads `-`.
 */
fun logDescription(patch: LoggedPatch): String = buildString {
    append("> PATCH ").append(titleSlug(patch.animeTitle).ifEmpty { "anime" })
    val u = patch.update
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

/** The right side of an answered PATCH: `✓ 214ms`, or `✗ <message>`; null while it is still out. */
fun logResult(outcome: PatchOutcome): String? = when (outcome) {
    PatchOutcome.Sent -> null
    is PatchOutcome.Accepted -> "✓ ${outcome.millis}ms"
    is PatchOutcome.Refused -> "✗ ${outcome.message}"
}

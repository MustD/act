package io.challenge_workshop.mal_ui.animepage

/**
 * What the log bar says: the last `PATCH` the save loop sent, and whether any save is still going.
 *
 * Fed from [AnimePageRepository]'s save loop alone — never from the HTTP client — so a GET is never
 * in it. Separate from the open pages because it outlives them: a save keeps running after its
 * page is closed, and the bar is the only place left that shows it. Ends with the Session.
 */
data class SaveLog(
    /** Null before the first save of the Session. */
    val last: LoggedSave? = null,
    /** Whether a save is unfinished for **any** anime, open or not. */
    val pending: Boolean = false,
)

/** One `PATCH` the save loop sent. */
data class LoggedSave(
    val animeTitle: String,
    /** The fields that went on the wire. */
    val update: ListEntryUpdate,
    val outcome: SaveOutcome,
)

sealed interface SaveOutcome {
    /** Sent, not answered yet. */
    data object Sent : SaveOutcome

    /** MAL accepted it, [millis] after it was sent. */
    data class Accepted(val millis: Long) : SaveOutcome

    /** MAL refused it, or it never got there; [message] is in words. */
    data class Refused(val message: String) : SaveOutcome
}

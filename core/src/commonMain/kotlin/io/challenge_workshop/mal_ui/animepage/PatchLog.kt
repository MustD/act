package io.challenge_workshop.mal_ui.animepage

/**
 * What the log bar says: the last `PATCH` a Save sent, and whether any Save is still going.
 *
 * Fed from [ListEntrySaves] alone — never from the HTTP client — so a GET is never
 * in it. Separate from the open pages because it outlives them: a Save keeps running after its
 * page is closed, and the bar is the only place left that shows it. Ends with the Session.
 */
data class PatchLog(
    /** Null before the first PATCH of the Session. */
    val last: LoggedPatch? = null,
    /** Whether a Save is unfinished for **any** anime, open or not. */
    val pending: Boolean = false,
)

/** One `PATCH` a Save sent. */
data class LoggedPatch(
    val animeTitle: String,
    /** The fields that went on the wire. */
    val update: ListEntryUpdate,
    val outcome: PatchOutcome,
)

sealed interface PatchOutcome {
    /** Sent, not answered yet. */
    data object Sent : PatchOutcome

    /** MAL accepted it, [millis] after it was sent. */
    data class Accepted(val millis: Long) : PatchOutcome

    /** MAL refused it, or it never got there; [message] is in words. */
    data class Refused(val message: String) : PatchOutcome
}

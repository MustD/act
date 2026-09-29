package io.challenge_workshop.mal_ui.session

import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [block] and answers what it failed with, as the text a screen shows — or null when it worked.
 *
 * Cancellation is rethrown, not reported: ending a Sign-in or an operation is routine, and turning it
 * into a failure would put "job was cancelled" in an error card. Shared by `SignIn` and
 * [SessionControls], which keep separate statuses on purpose (ADR-0005) but must not disagree on
 * what counts as a failure.
 */
internal suspend fun failureOf(block: suspend () -> Unit): String? =
    try {
        block()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e.message ?: e.toString()
    }

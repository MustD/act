package io.challenge_workshop.mal_ui.session

import io.challenge_workshop.mal_ui.animelist.AnimeListLayout
import io.challenge_workshop.mal_ui.mal.MalTokens
import io.challenge_workshop.mal_ui.mal.MalUser
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * The Session, the Pending Authorization and the Layout preference, as JSON in a
 * [KeyValueStore].
 *
 * Two policies live here rather than in callers:
 *
 *  - **Keys are version-stamped.** A format change becomes a clean re-login instead of a
 *    deserialization crash loop, because the new key is simply absent.
 *  - **A corrupt value reads as absent, and is deleted.** Throwing would give a crash loop
 *    that no amount of restarting escapes; leaving the bad value would re-read it on every
 *    launch. Discarding is safe for every record here: the two about the user are recoverable
 *    by signing in again, and the preference falls back to a default the user can re-pick.
 *
 * [clock] is injected, and stamps [StoredSession.obtainedAtEpochMs] /
 * [PendingAuthorization.startedAtEpochMs] here rather than at call sites, so no caller can
 * write a stamp that disagrees with the value it is stored beside.
 */
class JsonTokenStore(
    private val kv: KeyValueStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val clock: Clock = Clock.System,
) {
    companion object {
        const val SESSION_KEY: String = "mal.session.v1"
        const val PENDING_KEY: String = "mal.pending.v1"
        /** Only so [discardLegacyClientId] can name the old record; nothing reads or writes it. */
        const val CLIENT_ID_KEY: String = "mal.clientId.v1"
        const val LAYOUT_KEY: String = "mal.layout.v1"
    }

    suspend fun readSession(): StoredSession? = readOrDiscard(SESSION_KEY)

    /**
     * Writes a Session verbatim, stamp included. Only for callers that are deliberately preserving an
     * existing [StoredSession.obtainedAtEpochMs]; everything else should use the stamping overload.
     */
    suspend fun writeSession(session: StoredSession) {
        write(SESSION_KEY, session)
    }

    /** Writes a Session, stamping the access token's issue time from [clock]. */
    suspend fun writeSession(tokens: MalTokens, user: MalUser?): StoredSession =
        StoredSession(
            tokens = tokens,
            user = user,
            obtainedAtEpochMs = clock.now().toEpochMilliseconds(),
        ).also { write(SESSION_KEY, it) }

    /**
     * Replaces the token pair on the stored Session, restamping the issue time, and keeps the
     * cached user. Returns null — writing nothing — when no Session is stored, which is what a
     * refresh racing a sign-out looks like.
     */
    suspend fun updateTokens(tokens: MalTokens): StoredSession? =
        readSession()?.let { writeSession(tokens, it.user) }

    /** Replaces the cached user without disturbing the tokens or their issue stamp. */
    suspend fun updateUser(user: MalUser?): StoredSession? =
        readSession()?.copy(user = user)?.also { write(SESSION_KEY, it) }

    suspend fun clearSession() = kv.remove(SESSION_KEY)

    suspend fun readPending(): PendingAuthorization? = readOrDiscard(PENDING_KEY)

    suspend fun writePending(
        codeVerifier: String,
        state: String,
        redirectUri: String,
        clientId: String,
    ): PendingAuthorization =
        PendingAuthorization(
            codeVerifier = codeVerifier,
            state = state,
            redirectUri = redirectUri,
            clientId = clientId,
            startedAtEpochMs = clock.now().toEpochMilliseconds(),
        ).also { write(PENDING_KEY, it) }

    suspend fun clearPending() = kv.remove(PENDING_KEY)

    /**
     * Removes the record an earlier build kept for the Client ID the user typed. The Client ID is now
     * fixed at build time, and a leftover would make this device answer "what does this app store"
     * differently from every other.
     */
    suspend fun discardLegacyClientId() = kv.remove(CLIENT_ID_KEY)

    /**
     * How the user last chose to read their Anime List, or [AnimeListLayout.Cards] if they never
     * have on this device.
     *
     * **Never null and never an error.** Absent, corrupt, or a Layout a later build wrote that this
     * one has no name for all give the default: a preference is not worth a failed read for a
     * caller to handle, let alone the crash loop an unknown enum value would otherwise be.
     */
    suspend fun readLayout(): AnimeListLayout =
        readOrDiscard<AnimeListLayout>(LAYOUT_KEY) ?: AnimeListLayout.Cards

    /** Writes the Layout. Presentation only — nothing here is ever sent to MAL. */
    suspend fun writeLayout(layout: AnimeListLayout) {
        write(LAYOUT_KEY, layout)
    }

    /**
     * Everything this store owns **about the user**. Used when a refresh is rejected and on sign-out.
     *
     * Deliberately not the Layout: it is a device preference, and one that reset on every
     * sign-out would be a choice the user has to make again for no reason they can see.
     */
    suspend fun clear() {
        clearSession()
        clearPending()
    }

    private suspend inline fun <reified T> readOrDiscard(key: String): T? {
        val raw = kv.read(key) ?: return null
        return try {
            json.decodeFromString<T>(raw)
        } catch (_: Exception) {
            kv.remove(key)
            null
        }
    }

    private suspend inline fun <reified T> write(key: String, value: T) {
        kv.write(key, json.encodeToString(value))
    }
}

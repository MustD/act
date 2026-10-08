@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.challenge_workshop.mal_ui.session

/**
 * Presence is probed separately from the value because a `js()` function's return type has to be
 * a type that crosses the Wasm boundary directly; `String` does, and a null-vs-empty distinction
 * inside one call would not. Two `sessionStorage` reads cost nothing at this volume, and the
 * alternative — treating `""` as absent — would silently swallow a legitimately empty value.
 */
private fun sessionStorageHas(key: String): Boolean = js("window.sessionStorage.getItem(key) !== null")

private fun sessionStorageGet(key: String): String = js("window.sessionStorage.getItem(key)")

private fun sessionStorageSet(key: String, value: String) {
    js("window.sessionStorage.setItem(key, value)")
}

private fun sessionStorageRemove(key: String) {
    js("window.sessionStorage.removeItem(key)")
}

/**
 * Web [KeyValueStore] for what belongs to one tab: `sessionStorage`, which holds the Pending
 * Authorization and nothing else. The Session, Layout and Theme are in [LocalStorageKeyValueStore];
 * see `docs/adr/0007-web-session-in-local-storage.md`.
 *
 * Per-tab is the point: shared between tabs, two simultaneous sign-ins would overwrite each other's
 * PKCE verifier, and the full-page-redirect fallback needs the verifier to survive a document that
 * is destroyed by design.
 *
 * **A popup gets its own copy of this storage, not a shared view.** So the popup document must
 * never read or clear the Pending Authorization: its clear would not reach the opener.
 */
class SessionStorageKeyValueStore(private val namespace: String) : KeyValueStore {

    private fun scoped(key: String) = "$namespace.$key"

    override suspend fun read(key: String): String? =
        scoped(key).let { if (sessionStorageHas(it)) sessionStorageGet(it) else null }

    override suspend fun write(key: String, value: String) = sessionStorageSet(scoped(key), value)

    override suspend fun remove(key: String) = sessionStorageRemove(scoped(key))
}

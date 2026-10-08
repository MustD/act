@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.challenge_workshop.mal_ui.session

// Presence is probed separately from the value for the reason `SessionStorageKeyValueStore` gives.
private fun localStorageHas(key: String): Boolean = js("window.localStorage.getItem(key) !== null")

private fun localStorageGet(key: String): String = js("window.localStorage.getItem(key)")

private fun localStorageSet(key: String, value: String) {
    js("window.localStorage.setItem(key, value)")
}

private fun localStorageRemove(key: String) {
    js("window.localStorage.removeItem(key)")
}

/**
 * Web [KeyValueStore] for everything durable: the Session, the Layout and the Theme, in
 * `localStorage`, so a new tab or a browser restart finds them. See
 * `docs/adr/0007-web-session-in-local-storage.md`.
 *
 * The Pending Authorization does **not** go here — `JsonTokenStore` routes it to
 * [SessionStorageKeyValueStore], which is per-tab.
 */
class LocalStorageKeyValueStore(private val namespace: String) : KeyValueStore {

    private fun scoped(key: String) = "$namespace.$key"

    override suspend fun read(key: String): String? =
        scoped(key).let { if (localStorageHas(it)) localStorageGet(it) else null }

    override suspend fun write(key: String, value: String) = localStorageSet(scoped(key), value)

    override suspend fun remove(key: String) = localStorageRemove(scoped(key))
}

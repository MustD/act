@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.challenge_workshop.mal_ui.session

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.js.JsAny

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
 * Calls [onChange] for each `storage` event on `localStorage` — which the browser fires in every
 * *other* document of the origin, never the one that wrote. [onChange] gets the raw key (`""` for
 * `localStorage.clear()`, which names none), whether there is a new value, and that value.
 */
private fun watchLocalStorage(onChange: (String, Boolean, String) -> Unit): JsAny = js(
    """{
        var listener = function (e) {
            if (e.storageArea !== window.localStorage) return;
            var hasValue = e.newValue !== null;
            onChange(e.key === null ? '' : String(e.key), hasValue, hasValue ? String(e.newValue) : '');
        };
        window.addEventListener('storage', listener);
        return listener;
    }""",
)

private fun unwatchLocalStorage(listener: JsAny): Unit =
    js("{ window.removeEventListener('storage', listener); }")

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

    /** From the browser's `storage` event: another tab's writes, removals and `clear()`, not this tab's own. */
    override fun changes(key: String): Flow<String?> = callbackFlow {
        val target = scoped(key)
        val listener = watchLocalStorage { changed, hasValue, value ->
            // A cleared storage names no key and has removed ours along with the rest.
            if (changed == target || changed.isEmpty()) trySend(if (hasValue) value else null)
        }
        awaitClose { unwatchLocalStorage(listener) }
    }
}

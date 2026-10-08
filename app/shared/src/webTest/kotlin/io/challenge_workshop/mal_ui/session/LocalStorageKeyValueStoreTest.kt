@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.challenge_workshop.mal_ui.session

import io.challenge_workshop.mal_ui.animelist.AnimeListLayout
import io.challenge_workshop.mal_ui.mal.MalTokens
import io.challenge_workshop.mal_ui.theme.Theme
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private fun localGet(key: String): String? = if (localHas(key)) localRaw(key) else null
private fun localHas(key: String): Boolean = js("window.localStorage.getItem(key) !== null")
private fun localRaw(key: String): String = js("window.localStorage.getItem(key)")
private fun sessionGet(key: String): String? = if (sessionHas(key)) sessionRaw(key) else null
private fun sessionHas(key: String): Boolean = js("window.sessionStorage.getItem(key) !== null")
private fun sessionRaw(key: String): String = js("window.sessionStorage.getItem(key)")
private fun sessionPut(key: String, value: String) {
    js("window.sessionStorage.setItem(key, value)")
}

/** A real one never fires in the document that wrote, so this stands in for another tab. */
private fun dispatchStorageEvent(key: String, newValue: String?) {
    js("""{
        var init = { key: key, newValue: newValue, storageArea: window.localStorage };
        window.dispatchEvent(new StorageEvent('storage', init));
    }""")
}

/** Runs in a real browser, against real `localStorage`. */
class LocalStorageKeyValueStoreTest {

    @Test
    fun satisfies_the_key_value_store_contract() = runTest {
        assertKeyValueStoreRoundTrip(LocalStorageKeyValueStore("local-contract-test"))
    }

    @Test
    fun a_stored_empty_string_is_distinguishable_from_an_absent_key() = runTest {
        val store = LocalStorageKeyValueStore("local-empty-test")

        store.write("k", "")

        assertEquals("", store.read("k"))
        assertNull(store.read("absent"))
    }

    @Test
    fun namespaces_do_not_see_each_others_keys() = runTest {
        LocalStorageKeyValueStore("local-ns-a").write("shared", "a")
        LocalStorageKeyValueStore("local-ns-b").write("shared", "b")

        assertEquals("a", LocalStorageKeyValueStore("local-ns-a").read("shared"))
        assertEquals("b", LocalStorageKeyValueStore("local-ns-b").read("shared"))
    }

    @Test
    fun each_record_lands_in_the_backend_the_routing_rule_names() = runTest {
        val ns = "routing-test"
        val store = JsonTokenStore(LocalStorageKeyValueStore(ns), SessionStorageKeyValueStore(ns))
        val tokens = MalTokens("Bearer", 100, "access", "refresh")

        store.writeSession(tokens, null)
        store.writeLayout(AnimeListLayout.Cards)
        store.writeTheme(Theme.System)
        store.writePending("verifier", "state", "https://r", "client")

        for (key in listOf(JsonTokenStore.SESSION_KEY, JsonTokenStore.LAYOUT_KEY, JsonTokenStore.THEME_KEY)) {
            assertNotNull(localGet("$ns.$key"), key)
            assertNull(sessionGet("$ns.$key"), key)
        }
        assertNotNull(sessionGet("$ns.${JsonTokenStore.PENDING_KEY}"))
        assertNull(localGet("$ns.${JsonTokenStore.PENDING_KEY}"))

        store.clear()
        store.clearSession()
    }

    @Test
    fun startup_cleanup_removes_old_session_storage_records_and_copies_nothing() = runTest {
        val ns = "cleanup-test"
        val store = JsonTokenStore(LocalStorageKeyValueStore(ns), SessionStorageKeyValueStore(ns))
        for (key in listOf(JsonTokenStore.SESSION_KEY, JsonTokenStore.LAYOUT_KEY, JsonTokenStore.THEME_KEY)) {
            sessionPut("$ns.$key", "old")
        }

        store.discardLegacyTabScopedRecords()

        for (key in listOf(JsonTokenStore.SESSION_KEY, JsonTokenStore.LAYOUT_KEY, JsonTokenStore.THEME_KEY)) {
            assertNull(sessionGet("$ns.$key"), key)
            assertNull(localGet("$ns.$key"), key)
        }
    }

    @Test
    fun another_tabs_removal_of_a_key_emits_null_and_a_new_value_emits_it() = runTest {
        val store = LocalStorageKeyValueStore("changes-test")
        val seen = mutableListOf<String?>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { store.changes("k").toList(seen) }
        yield() // let the flow's producer register its listener

        dispatchStorageEvent("changes-test.other", null)
        dispatchStorageEvent("changes-test.k", "v")
        dispatchStorageEvent("changes-test.k", null)
        yield()
        job.cancel()

        assertEquals(listOf("v", null), seen)
    }

    @Test
    fun the_stores_own_writes_do_not_emit() = runTest {
        val store = LocalStorageKeyValueStore("own-writes-test")
        val seen = mutableListOf<String?>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { store.changes("k").toList(seen) }
        yield() // let the flow's producer register its listener

        store.write("k", "v")
        store.remove("k")
        yield()
        job.cancel()

        assertEquals(emptyList(), seen)
    }
}

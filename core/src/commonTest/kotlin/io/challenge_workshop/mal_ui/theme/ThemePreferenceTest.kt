@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.challenge_workshop.mal_ui.theme

import io.challenge_workshop.mal_ui.session.JsonTokenStore
import io.challenge_workshop.mal_ui.session.KeyValueStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Mirrors `LayoutPreferenceTest`: the ordering of a choice against a startup read still in flight. */
class ThemePreferenceTest {

    @Test
    fun the_stored_theme_replaces_the_default_once_it_is_read() = runTest {
        val kv = GatedKeyValueStore(stored = Theme.Light)
        val preference = ThemePreference(JsonTokenStore(kv), this)
        runCurrent()

        assertEquals(Theme.System, preference.value.value, "before the read lands")

        kv.releaseReads()
        advanceUntilIdle()

        assertEquals(Theme.Light, preference.value.value)
    }

    @Test
    fun a_device_that_has_never_chosen_follows_the_system() = runTest {
        val kv = GatedKeyValueStore()
        val preference = ThemePreference(JsonTokenStore(kv), this)

        kv.releaseReads()
        advanceUntilIdle()

        assertEquals(Theme.System, preference.value.value)
    }

    @Test
    fun a_theme_chosen_before_the_stored_one_is_read_is_not_overwritten() = runTest {
        val kv = GatedKeyValueStore(stored = Theme.Light)
        val preference = ThemePreference(JsonTokenStore(kv), this)
        runCurrent()

        preference.choose(Theme.Dark)
        assertEquals(Theme.Dark, preference.value.value, "the choice must be immediate")

        kv.releaseReads()
        advanceUntilIdle()
        assertEquals(Theme.Dark, preference.value.value, "the startup read overwrote a choice")

        kv.releaseWrites()
        advanceUntilIdle()
        assertEquals(Theme.Dark, JsonTokenStore(kv).readTheme())
    }

    @Test
    fun picking_the_theme_already_on_screen_still_reaches_the_store() = runTest {
        val kv = GatedKeyValueStore(stored = Theme.Light)
        val preference = ThemePreference(JsonTokenStore(kv), this)
        runCurrent()

        preference.choose(Theme.System)
        kv.releaseReads()
        kv.releaseWrites()
        advanceUntilIdle()

        assertEquals(Theme.System, JsonTokenStore(kv).readTheme())
    }

    @Test
    fun toggling_from_dark_goes_to_light_and_back() = runTest {
        val preference = ThemePreference(JsonTokenStore(GatedKeyValueStore().also { it.releaseAll() }), this)
        preference.choose(Theme.Dark)

        preference.toggle(showingDark = true)
        assertEquals(Theme.Light, preference.value.value)

        preference.toggle(showingDark = false)
        assertEquals(Theme.Dark, preference.value.value)
        advanceUntilIdle()
    }

    @Test
    fun toggling_from_system_goes_to_the_opposite_of_what_is_showing() = runTest {
        val kv = GatedKeyValueStore().also { it.releaseAll() }

        val onDarkDevice = ThemePreference(JsonTokenStore(kv), this)
        onDarkDevice.toggle(showingDark = true)
        assertEquals(Theme.Light, onDarkDevice.value.value)

        val onLightDevice = ThemePreference(JsonTokenStore(kv), this)
        onLightDevice.toggle(showingDark = false)
        assertEquals(Theme.Dark, onLightDevice.value.value)
        advanceUntilIdle()
    }

    @Test
    fun system_defers_to_the_device_and_the_others_do_not() {
        assertEquals(true, Theme.System.isDark(systemDark = true))
        assertEquals(false, Theme.System.isDark(systemDark = false))
        assertEquals(true, Theme.Dark.isDark(systemDark = false))
        assertEquals(false, Theme.Light.isDark(systemDark = true))
    }
}

private class GatedKeyValueStore(stored: Theme? = null) : KeyValueStore {
    private val entries = mutableMapOf<String, String>().apply {
        stored?.let { put(JsonTokenStore.THEME_KEY, "\"${it.name}\"") }
    }
    private val readGate = CompletableDeferred<Unit>()
    private val writeGate = CompletableDeferred<Unit>()

    fun releaseReads() { readGate.complete(Unit) }
    fun releaseWrites() { writeGate.complete(Unit) }
    fun releaseAll() { releaseReads(); releaseWrites() }

    override suspend fun read(key: String): String? {
        readGate.await()
        return entries[key]
    }

    override suspend fun write(key: String, value: String) {
        writeGate.await()
        entries[key] = value
    }

    override suspend fun remove(key: String) {
        entries.remove(key)
    }
}

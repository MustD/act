package io.challenge_workshop.mal_ui.theme

import io.challenge_workshop.mal_ui.session.JsonTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The user's [Theme], as a `StateFlow` and a way to change it. Shaped like
 * [io.challenge_workshop.mal_ui.animelist.LayoutPreference], and for the same reasons: process-scoped,
 * switch-then-write-behind, and a startup read that never overwrites a choice already made.
 *
 * Not an input to the Screen State: it applies to all four destinations, so `App()` reads it directly.
 *
 * @param scope process-scoped, `Dispatchers.Main.immediate` in the app, which is what confines [chosen]
 * to one thread.
 */
class ThemePreference(
    private val store: JsonTokenStore,
    private val scope: CoroutineScope,
) {
    private val _value = MutableStateFlow(Theme.System)

    /** The Theme, as the user last left it; [Theme.System] until the stored record is read. */
    val value: StateFlow<Theme> = _value.asStateFlow()

    /** Whether the user has picked a Theme, which is what the startup read must not overwrite. */
    private var chosen = false

    init {
        scope.launch {
            val stored = store.readTheme()
            if (!chosen) _value.value = stored
        }
    }

    /** Switches the Theme at once and writes it behind. Every call writes, even one that changes nothing on screen. */
    fun choose(theme: Theme) {
        chosen = true
        _value.value = theme
        scope.launch { store.writeTheme(theme) }
    }

    /**
     * Dark ↔ light. [showingDark] is what is on screen now, which only differs from the choice when it
     * is [Theme.System]: from there this goes to the opposite of what is showing.
     */
    fun toggle(showingDark: Boolean) {
        choose(if (showingDark) Theme.Light else Theme.Dark)
    }
}

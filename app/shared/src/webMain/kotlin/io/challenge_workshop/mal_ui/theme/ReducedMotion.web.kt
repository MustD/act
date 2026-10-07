package io.challenge_workshop.mal_ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

private fun prefersReducedMotion(): Boolean =
    js("window.matchMedia('(prefers-reduced-motion: reduce)').matches")

/** Returns the query so the listener can be removed again by reference. */
private fun watchReducedMotion(onChange: (Boolean) -> Unit): JsAny = js(
    """{
        var query = window.matchMedia('(prefers-reduced-motion: reduce)');
        var listener = function (e) { onChange(e.matches); };
        query.addEventListener('change', listener);
        return { query: query, listener: listener };
    }""",
)

private fun unwatchReducedMotion(handle: JsAny): Unit =
    js("{ handle.query.removeEventListener('change', handle.listener); }")

@Composable
actual fun systemReducedMotion(): Boolean {
    var reduced by remember { mutableStateOf(prefersReducedMotion()) }
    DisposableEffect(Unit) {
        val handle = watchReducedMotion { reduced = it }
        onDispose { unwatchReducedMotion(handle) }
    }
    return reduced
}

package net.io_workshop.act.theme

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Turns a plain vertical mouse wheel over a horizontal row into scrolling of that row's [state].
 * `horizontalScroll` alone answers only Shift+wheel and horizontal swipes, so a long row cannot be
 * scrolled with a mouse. At either end the event is left unconsumed, so the enclosing vertical
 * scroll takes it and a cursor resting on the row never traps the page.
 */
fun Modifier.wheelHorizontalScroll(state: ScrollState): Modifier = pointerInput(state) {
    val stepPx = WHEEL_STEP.toPx()
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type != PointerEventType.Scroll) continue
            val change = event.changes.firstOrNull() ?: continue
            val delta = change.scrollDelta
            // A horizontal delta is already the row's own business; only the vertical wheel is converted.
            if (delta.x != 0f || delta.y == 0f) continue
            val toward = delta.y
            val canMove = if (toward > 0f) state.canScrollForward else state.canScrollBackward
            if (!canMove) continue
            state.dispatchRawDelta(toward * stepPx)
            change.consume()
        }
    }
}

private val WHEEL_STEP = 48.dp

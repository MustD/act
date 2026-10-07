package io.challenge_workshop.mal_ui.animepage

import androidx.compose.ui.input.key.Key
import io.challenge_workshop.mal_ui.animelist.ANIME_LIST_FILTERS
import io.challenge_workshop.mal_ui.animelist.WatchStatus

/** What a bound key asks for. Esc is not here: it is the Anime Page's own back, handled where it was. */
internal sealed interface KeyCommand {
    /** Select the next (+1) or previous (-1) loaded entry and open it. */
    data class Move(val delta: Int) : KeyCommand

    /** A change to the open page's List Entry, through the same actions the controls use. */
    data class Edit(val edit: ListEdit) : KeyCommand

    /** The previous (-1) or next (+1) Watch Status, in chip order. */
    data class StepWatchStatus(val delta: Int) : KeyCommand
}

internal fun keyCommand(key: Key): KeyCommand? = when (key) {
    Key.J, Key.DirectionDown -> KeyCommand.Move(1)
    Key.K, Key.DirectionUp -> KeyCommand.Move(-1)
    Key.Plus, Key.Equals, Key.NumPadAdd -> KeyCommand.Edit(ListEdit.AddEpisodes(1))
    Key.Minus, Key.NumPadSubtract -> KeyCommand.Edit(ListEdit.AddEpisodes(-1))
    Key.LeftBracket -> KeyCommand.StepWatchStatus(-1)
    Key.RightBracket -> KeyCommand.StepWatchStatus(1)
    else -> SCORE_KEYS[key]?.let { KeyCommand.Edit(ListEdit.SetScore(it)) }
}

private val SCORE_KEYS: Map<Key, Int> = mapOf(
    Key.One to 1, Key.Two to 2, Key.Three to 3, Key.Four to 4, Key.Five to 5,
    Key.Six to 6, Key.Seven to 7, Key.Eight to 8, Key.Nine to 9, Key.Zero to 10,
    Key.NumPad1 to 1, Key.NumPad2 to 2, Key.NumPad3 to 3, Key.NumPad4 to 4, Key.NumPad5 to 5,
    Key.NumPad6 to 6, Key.NumPad7 to 7, Key.NumPad8 to 8, Key.NumPad9 to 9, Key.NumPad0 to 10,
)

private val WATCH_STATUS_ORDER: List<WatchStatus> = ANIME_LIST_FILTERS.filterNotNull()

/** [current] moved by [delta] along the chips, stopping at either end; an unlisted Watch Status starts from the first. */
internal fun steppedWatchStatus(current: WatchStatus, delta: Int): WatchStatus {
    val at = WATCH_STATUS_ORDER.indexOf(current)
    if (at < 0) return WATCH_STATUS_ORDER.first()
    return WATCH_STATUS_ORDER[(at + delta).coerceIn(0, WATCH_STATUS_ORDER.lastIndex)]
}

/**
 * The index to open when moving [delta] from [current] (-1: nothing open) over [size] loaded
 * entries. Stops at the ends; null when there is nothing to open.
 */
internal fun adjacentIndex(current: Int, delta: Int, size: Int): Int? {
    if (size == 0) return null
    if (current < 0) return if (delta > 0) 0 else size - 1
    return (current + delta).coerceIn(0, size - 1)
}

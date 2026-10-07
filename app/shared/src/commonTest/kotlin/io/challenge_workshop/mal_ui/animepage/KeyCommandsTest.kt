package io.challenge_workshop.mal_ui.animepage

import androidx.compose.ui.input.key.Key
import io.challenge_workshop.mal_ui.animelist.WatchStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeyCommandsTest {

    @Test
    fun j_and_down_go_to_the_next_entry_and_k_and_up_to_the_previous() {
        assertEquals(KeyCommand.Move(1), keyCommand(Key.J))
        assertEquals(KeyCommand.Move(1), keyCommand(Key.DirectionDown))
        assertEquals(KeyCommand.Move(-1), keyCommand(Key.K))
        assertEquals(KeyCommand.Move(-1), keyCommand(Key.DirectionUp))
    }

    @Test
    fun plus_equals_and_minus_move_episodes_by_one() {
        assertEquals(KeyCommand.Edit(ListEdit.AddEpisodes(1)), keyCommand(Key.Plus))
        assertEquals(KeyCommand.Edit(ListEdit.AddEpisodes(1)), keyCommand(Key.Equals))
        assertEquals(KeyCommand.Edit(ListEdit.AddEpisodes(1)), keyCommand(Key.NumPadAdd))
        assertEquals(KeyCommand.Edit(ListEdit.AddEpisodes(-1)), keyCommand(Key.Minus))
        assertEquals(KeyCommand.Edit(ListEdit.AddEpisodes(-1)), keyCommand(Key.NumPadSubtract))
    }

    @Test
    fun digits_score_and_zero_means_ten() {
        assertEquals(KeyCommand.Edit(ListEdit.SetScore(1)), keyCommand(Key.One))
        assertEquals(KeyCommand.Edit(ListEdit.SetScore(9)), keyCommand(Key.Nine))
        assertEquals(KeyCommand.Edit(ListEdit.SetScore(10)), keyCommand(Key.Zero))
        assertEquals(KeyCommand.Edit(ListEdit.SetScore(5)), keyCommand(Key.NumPad5))
        assertEquals(KeyCommand.Edit(ListEdit.SetScore(10)), keyCommand(Key.NumPad0))
    }

    @Test
    fun brackets_step_the_watch_status() {
        assertEquals(KeyCommand.Status(-1), keyCommand(Key.LeftBracket))
        assertEquals(KeyCommand.Status(1), keyCommand(Key.RightBracket))
    }

    @Test
    fun other_keys_are_not_commands() {
        assertNull(keyCommand(Key.Y))
        assertNull(keyCommand(Key.Enter))
        assertNull(keyCommand(Key.Escape))
    }

    @Test
    fun watch_status_steps_in_chip_order_and_stops_at_the_ends() {
        assertEquals(WatchStatus.Completed, steppedWatchStatus(WatchStatus.Watching, 1))
        assertEquals(WatchStatus.Watching, steppedWatchStatus(WatchStatus.Completed, -1))
        assertEquals(WatchStatus.Watching, steppedWatchStatus(WatchStatus.Watching, -1))
        assertEquals(WatchStatus.PlanToWatch, steppedWatchStatus(WatchStatus.PlanToWatch, 1))
        assertEquals(WatchStatus.Watching, steppedWatchStatus(WatchStatus.Unknown, 1))
    }

    @Test
    fun the_entry_index_moves_and_stops_at_the_loaded_ends() {
        assertEquals(1, adjacentIndex(current = 0, delta = 1, size = 3))
        assertEquals(0, adjacentIndex(current = 0, delta = -1, size = 3))
        assertEquals(2, adjacentIndex(current = 2, delta = 1, size = 3))
        assertEquals(0, adjacentIndex(current = -1, delta = 1, size = 3), "nothing open: j opens the first")
        assertEquals(2, adjacentIndex(current = -1, delta = -1, size = 3), "nothing open: k opens the last")
        assertNull(adjacentIndex(current = -1, delta = 1, size = 0))
    }
}

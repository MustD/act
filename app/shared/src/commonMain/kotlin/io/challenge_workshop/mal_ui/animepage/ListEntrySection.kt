package io.challenge_workshop.mal_ui.animepage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.challenge_workshop.mal_ui.animelist.ANIME_LIST_FILTERS
import io.challenge_workshop.mal_ui.animelist.WatchStatus
import io.challenge_workshop.mal_ui.animelist.filterLabel
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_EPISODES_MINUS_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_EPISODES_PLUS_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_EPISODES_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_FINISH_DATE_CLEAR_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_FINISH_DATE_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_LIST_STATUS_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_SAVE_ERROR_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_SAVING_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_SCORE_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_START_DATE_CLEAR_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_START_DATE_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_STATUS_TAG
import io.challenge_workshop.mal_ui.auth.ErrorCard
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * The user's List Entry, editable once [AnimePage.canEdit].
 *
 * It draws [AnimePage.shownListStatus] — the pending target while a save is under way — so a change
 * shows the moment it is made, and says "Saving…" for as long as MAL has not confirmed it. Before
 * the fetch has succeeded the same fields are drawn, disabled: the row the page opened from may be
 * stale and has no dates. Absent for an anime that is not on the list; adding one is not here yet.
 */
@Composable
internal fun ListEntrySection(page: AnimePage, onEdit: (ListEdit) -> Unit) {
    Column(
        Modifier.fillMaxWidth().testTag(ANIME_PAGE_LIST_STATUS_TAG),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Your list entry", style = MaterialTheme.typography.titleMedium)
            if (page.isSaving) {
                Text(
                    "Saving…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag(ANIME_PAGE_SAVING_TAG),
                )
            }
        }
        val status = page.shownListStatus
        if (status == null) {
            Text(
                "Not on your list.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        page.save.error?.let { message ->
            Column(Modifier.testTag(ANIME_PAGE_SAVE_ERROR_TAG)) { ErrorCard("Could not save that change", message) }
        }
        val enabled = page.canEdit
        Field("Status") { WatchStatusPicker(status.watchStatus, enabled) { onEdit(ListEdit.SetWatchStatus(it)) } }
        Field("Episodes") { EpisodesField(status.episodesWatched, page.totalEpisodes, enabled, onEdit) }
        Field("Score") { ScorePicker(status.score, enabled) { onEdit(ListEdit.SetScore(it)) } }
        Field("Started") {
            DateField(
                date = status.startDate,
                enabled = enabled,
                tag = ANIME_PAGE_START_DATE_TAG,
                clearTag = ANIME_PAGE_START_DATE_CLEAR_TAG,
                onPick = { onEdit(ListEdit.SetStartDate(it)) },
            )
        }
        Field("Finished") {
            DateField(
                date = status.finishDate,
                enabled = enabled,
                tag = ANIME_PAGE_FINISH_DATE_TAG,
                clearTag = ANIME_PAGE_FINISH_DATE_CLEAR_TAG,
                onPick = { onEdit(ListEdit.SetFinishDate(it)) },
            )
        }
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        content()
    }
}

/** A button that opens a menu of [choices]; the button says what is chosen. */
@Composable
private fun <T> MenuPicker(
    current: String,
    choices: List<T>,
    label: (T) -> String,
    enabled: Boolean,
    tag: String,
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.testTag(tag)) {
            Text(current)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (choice in choices) {
                DropdownMenuItem(
                    text = { Text(label(choice)) },
                    onClick = {
                        open = false
                        onPick(choice)
                    },
                )
            }
        }
    }
}

/** The five Watch Statuses one can pick — the filters without All, and without the one MAL may add later. */
private val WATCH_STATUS_CHOICES: List<WatchStatus> = ANIME_LIST_FILTERS.filterNotNull()

@Composable
private fun WatchStatusPicker(current: WatchStatus, enabled: Boolean, onPick: (WatchStatus) -> Unit) {
    MenuPicker(current.filterLabel(), WATCH_STATUS_CHOICES, { it.filterLabel() }, enabled, ANIME_PAGE_STATUS_TAG, onPick)
}

@Composable
private fun ScorePicker(current: Int, enabled: Boolean, onPick: (Int) -> Unit) {
    MenuPicker(scoreText(current), SCORE_CHOICES, ::scoreText, enabled, ANIME_PAGE_SCORE_TAG, onPick)
}

/**
 * − and +, and a number field between them, limited to `0…total` — or `0…` while the total is
 * unknown (0). The limit is applied by the edit, in `:core`; the field only has to say what was typed.
 *
 * Typing is **committed on Done or when focus leaves**, not per keystroke: "12" would otherwise be
 * saved as 1 first. The text follows the shown value again the moment that changes, so a clamped
 * entry reads as what it became.
 */
@Composable
private fun EpisodesField(watched: Int, total: Int, enabled: Boolean, onEdit: (ListEdit) -> Unit) {
    var text by remember(watched) { mutableStateOf(watched.toString()) }
    fun commit() {
        text.toIntOrNull()?.let { if (it != watched) onEdit(ListEdit.SetEpisodes(it)) }
        text = watched.toString()
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(
            onClick = { onEdit(ListEdit.AddEpisodes(-1)) },
            enabled = enabled && watched > 0,
            modifier = Modifier.testTag(ANIME_PAGE_EPISODES_MINUS_TAG).semantics { contentDescription = "One episode fewer" },
        ) { Text("−") }
        OutlinedTextField(
            value = text,
            onValueChange = { input -> text = input.filter { it.isDigit() }.take(5) },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            modifier = Modifier.width(88.dp).testTag(ANIME_PAGE_EPISODES_TAG).onFocusChanged { if (!it.isFocused) commit() },
        )
        TextButton(
            onClick = { onEdit(ListEdit.AddEpisodes(1)) },
            enabled = enabled && (total == 0 || watched < total),
            modifier = Modifier.testTag(ANIME_PAGE_EPISODES_PLUS_TAG).semantics { contentDescription = "One episode more" },
        ) { Text("+") }
        Text("/ ${if (total > 0) total.toString() else "?"}", style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * A date as MAL holds it — shown verbatim, so a partial `2024` reads as `2024` — a button that opens
 * a Material 3 [DatePicker] in a dialog, and a clear action. Only a full date can be picked, and
 * picking one replaces whatever was there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(date: String?, enabled: Boolean, tag: String, clearTag: String, onPick: (LocalDate?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = { picking = true }, enabled = enabled, modifier = Modifier.testTag(tag)) {
            Text(date ?: NO_DATE)
        }
        if (date != null) {
            TextButton(
                onClick = { onPick(null) },
                enabled = enabled,
                modifier = Modifier.testTag(clearTag).semantics { contentDescription = "Clear date" },
            ) { Text("Clear") }
        }
    }
    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        picking = false
                        state.selectedDateMillis?.let {
                            onPick(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date)
                        }
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

private const val NO_DATE = "—"

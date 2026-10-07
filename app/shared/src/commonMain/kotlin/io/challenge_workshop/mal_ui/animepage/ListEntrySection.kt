package io.challenge_workshop.mal_ui.animepage

import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_FINISH_DATE_SET_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_START_DATE_SET_TAG
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_SCORE_CLEAR_TAG
import io.challenge_workshop.mal_ui.theme.WatchStatusChips
import io.challenge_workshop.mal_ui.theme.TickingNumber
import io.challenge_workshop.mal_ui.theme.SectionLabel
import io.challenge_workshop.mal_ui.theme.ScoreCells
import io.challenge_workshop.mal_ui.theme.SavingIndicator
import io.challenge_workshop.mal_ui.theme.EpisodeCells
import io.challenge_workshop.mal_ui.theme.ActMedium
import io.challenge_workshop.mal_ui.theme.ActEasing
import io.challenge_workshop.mal_ui.theme.Act
import io.challenge_workshop.mal_ui.theme.motion
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.challenge_workshop.mal_ui.animelist.ANIME_LIST_FILTERS
import io.challenge_workshop.mal_ui.animelist.WatchStatus
import io.challenge_workshop.mal_ui.animelist.filterLabel
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_ADD_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_EPISODES_MINUS_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_EPISODES_PLUS_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_EPISODES_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_FINISH_DATE_CLEAR_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_FINISH_DATE_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_LIST_ENTRY_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_SAVE_ERROR_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_SCORE_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_START_DATE_CLEAR_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_START_DATE_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_WATCH_STATUS_TAG
import io.challenge_workshop.mal_ui.auth.ErrorCard
import io.challenge_workshop.mal_ui.auth.animePageSavingTag
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/** The sections of the List Entry, in the order they are drawn; the one a refused save is reported under comes from it. */
private enum class EntrySection { Episodes, WatchStatus, Score, Dates }

private fun ListEntryUpdate.sections(): Set<EntrySection> = buildSet {
    if (episodesWatched != null) add(EntrySection.Episodes)
    if (watchStatus != null) add(EntrySection.WatchStatus)
    if (score != null) add(EntrySection.Score)
    if (startDate != null || finishDate != null) add(EntrySection.Dates)
}

/**
 * The user's List Entry, editable once [AnimePage.canEdit]: `// episodes`, `// watch status`,
 * `// score` and `// dates`.
 *
 * It draws [AnimePage.shownListEntry] — the pending target while a save is under way — so a change
 * shows the moment it is made, and each section whose field is in [AnimePage.pendingChange] has a
 * saving indicator beside its label for as long as MAL has not confirmed it. A refused save shows
 * its error card under the first section it carried a field of, or under `// episodes` when it carried none.
 * Before the fetch has succeeded the same fields are drawn, disabled: the row the page opened from
 * may be stale and has no dates. For an anime that is not on the list it is an "Add to list as…"
 * picker and nothing else, until MAL confirms the add; before the fetch has landed it is neither.
 */
@Composable
internal fun ListEntrySection(page: AnimePage, onEdit: (ListEdit) -> Unit, onAdd: (WatchStatus) -> Unit) {
    val errorUnder = page.save.errorFields.sections().minOrNull() ?: EntrySection.Episodes
    val error = page.save.error
    @Composable
    fun ErrorUnder(section: EntrySection) {
        if (error != null && section == errorUnder) {
            Column(Modifier.testTag(ANIME_PAGE_SAVE_ERROR_TAG)) { ErrorCard("Could not save that change", error) }
        }
    }
    Column(
        Modifier.fillMaxWidth().testTag(ANIME_PAGE_LIST_ENTRY_TAG),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        // Keyed on what MAL has confirmed, not on the pending target: an add is not on the list, and
        // the editor stays away, until MAL says it is.
        if (page.listEntry == null) {
            // Until the fetch lands it is not known that the anime is off the list (a Related Anime
            // opens with no entry however it stands), so the picker is not offered.
            if (page.load != AnimePageLoad.Loaded) {
                ErrorUnder(EntrySection.Episodes)
                return@Column
            }
            Section("add to list", ANIME_PAGE_ADD_TAG, saving = page.isSaving) {
                ErrorUnder(errorUnder)
                AddToListPicker(
                    pending = page.save.target?.watchStatus,
                    enabled = !page.isSaving,
                    onPick = onAdd,
                )
            }
            return@Column
        }
        val entry = page.shownListEntry!!
        val pending = page.pendingChange
        val enabled = page.canEdit
        Section("episodes", ANIME_PAGE_EPISODES_TAG, saving = pending.episodesWatched != null) {
            ErrorUnder(EntrySection.Episodes)
            EpisodesBox(entry.episodesWatched, page.anime.totalEpisodes, enabled, onEdit)
        }
        Section("watch status", ANIME_PAGE_WATCH_STATUS_TAG, saving = pending.watchStatus != null) {
            ErrorUnder(EntrySection.WatchStatus)
            WatchStatusChips(
                options = WATCH_STATUS_CHOICES,
                selected = entry.watchStatus,
                key = { it.wireValue ?: "" },
                enabled = enabled,
                onPick = { onEdit(ListEdit.SetWatchStatus(it)) },
            )
        }
        Section("score", ANIME_PAGE_SCORE_TAG, saving = pending.score != null) {
            ErrorUnder(EntrySection.Score)
            ScoreEditor(entry.score, enabled) { onEdit(ListEdit.SetScore(it)) }
        }
        Section("dates", ANIME_PAGE_START_DATE_TAG, saving = false) {
            ErrorUnder(EntrySection.Dates)
            Column(
                Modifier.fillMaxWidth().background(Act.colors.sf, ActMedium).border(1.dp, Act.colors.ln, ActMedium),
            ) {
                DateRow(
                    "started", entry.startDate, enabled, ANIME_PAGE_START_DATE_TAG, ANIME_PAGE_START_DATE_SET_TAG,
                    ANIME_PAGE_START_DATE_CLEAR_TAG, saving = pending.startDate != null,
                ) { onEdit(ListEdit.SetStartDate(it)) }
                HorizontalDivider(color = Act.colors.ln)
                DateRow(
                    "finished", entry.finishDate, enabled, ANIME_PAGE_FINISH_DATE_TAG, ANIME_PAGE_FINISH_DATE_SET_TAG,
                    ANIME_PAGE_FINISH_DATE_CLEAR_TAG, saving = pending.finishDate != null,
                ) { onEdit(ListEdit.SetFinishDate(it)) }
            }
        }
    }
}

/**
 * A `// label` with its [SavingIndicator] on the right while [saving], then [content]. [tag] is the
 * section's control's, and names the indicator by [animePageSavingTag].
 */
@Composable
private fun Section(label: String, tag: String, saving: Boolean, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label) { SavingIndicator(saving, Modifier.testTag(animePageSavingTag(tag))) }
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

/** "Add to list as…": the five Watch Statuses, the button saying which one is being added while it saves. */
@Composable
private fun AddToListPicker(pending: WatchStatus?, enabled: Boolean, onPick: (WatchStatus) -> Unit) {
    MenuPicker(
        current = pending?.filterLabel() ?: "Choose…",
        choices = WATCH_STATUS_CHOICES,
        label = { it.filterLabel() },
        enabled = enabled,
        tag = ANIME_PAGE_ADD_TAG,
        onPick = onPick,
    )
}

/**
 * The Score: ten cells, MAL's label for the chosen one (`8 — Very Good`), and `clear`, which sets 0.
 * The label carries [ANIME_PAGE_SCORE_TAG].
 */
@Composable
private fun ScoreEditor(score: Int, enabled: Boolean, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScoreCells(score, enabled, onPick)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                scoreText(score),
                style = Act.type.scoreText,
                color = Act.colors.ink,
                modifier = Modifier.testTag(ANIME_PAGE_SCORE_TAG),
            )
            Text(
                "clear",
                style = Act.type.meta,
                color = Act.colors.dim,
                modifier = Modifier.testTag(ANIME_PAGE_SCORE_CLEAR_TAG)
                    .clickable(enabled = enabled && score != 0, role = Role.Button) { onPick(0) }
                    .padding(vertical = 8.dp),
            )
        }
    }
}

/**
 * The episodes box: − and + around the ticking number and `/ total`, one tappable cell per episode
 * (a bar over 60 or with no total), and the caption saying what the cells do. Both buttons and the
 * cells send the edit the number would: ± 1, or [ListEdit.SetEpisodes]. The limit is applied by the
 * edit, in `:core`. The number carries [ANIME_PAGE_EPISODES_TAG].
 */
@Composable
private fun EpisodesBox(watched: Int, total: Int, enabled: Boolean, onEdit: (ListEdit) -> Unit) {
    val c = Act.colors
    Column(
        Modifier.fillMaxWidth().background(c.sf, ActMedium).border(1.dp, c.ln, ActMedium).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(
                "−", "One episode fewer", filled = false, enabled = enabled && watched > 0,
                tag = ANIME_PAGE_EPISODES_MINUS_TAG,
            ) { onEdit(ListEdit.AddEpisodes(-1)) }
            Row(
                Modifier.weight(1f).semantics(mergeDescendants = true) {}.testTag(ANIME_PAGE_EPISODES_TAG),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom,
            ) {
                TickingNumber(watched, Act.type.bigNumber)
                Text(
                    " / ${if (total > 0) total.toString() else "?"}",
                    style = Act.type.body.copy(fontSize = 16.sp),
                    color = c.dim,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            StepButton(
                "+", "One episode more", filled = true, enabled = enabled && (total == 0 || watched < total),
                tag = ANIME_PAGE_EPISODES_PLUS_TAG,
            ) { onEdit(ListEdit.AddEpisodes(1)) }
        }
        EpisodeCells(watched, total, onPick = { onEdit(ListEdit.SetEpisodes(it)) }, enabled = enabled, maxCells = EPISODE_CELLS_MAX)
        val cells = total in 1..EPISODE_CELLS_MAX
        Text(
            when {
                cells -> "tap a cell to jump to that episode"
                total <= 0 -> "still airing — total unknown"
                else -> "${(watched * 100 / total).coerceIn(0, 100)}% watched"
            },
            style = Act.type.tiny,
            color = c.dim,
        )
    }
}

private const val EPISODE_CELLS_MAX = 60

/** A 52×52 − or +, which shrinks to 0.92 while pressed. */
@Composable
private fun StepButton(
    symbol: String,
    description: String,
    filled: Boolean,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    val c = Act.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, motion(tween(120, easing = ActEasing)), label = "stepPress")
    Box(
        Modifier.size(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(ActMedium)
            .background(if (filled) c.acc else Color.Transparent)
            .then(if (filled) Modifier else Modifier.border(1.dp, c.ln, ActMedium))
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag(tag)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(symbol, style = Act.type.bigNumber.copy(fontSize = 24.sp, lineHeight = 24.sp), color = if (filled) c.onAcc else c.ink) }
}

/**
 * A date as MAL holds it — shown verbatim, so a partial `2024` reads as `2024` — in a 48dp row with
 * `set today` when empty and `clear` when set. `set today` opens a Material 3 [DatePicker] in a
 * dialog rather than saving at once; only a full date can be picked, and picking one replaces
 * whatever was there. The value carries [tag], the actions [setTag] and [clearTag].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(
    label: String,
    date: String?,
    enabled: Boolean,
    tag: String,
    setTag: String,
    clearTag: String,
    saving: Boolean,
    onPick: (LocalDate?) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Act.type.meta, color = Act.colors.dim, modifier = Modifier.width(64.dp))
        Text(
            date ?: NO_DATE,
            style = Act.type.body,
            color = if (date != null) Act.colors.ink else Act.colors.dim,
            modifier = Modifier.testTag(tag),
        )
        SavingIndicator(saving, Modifier.padding(start = 8.dp).testTag(animePageSavingTag(tag)))
        Spacer(Modifier.weight(1f))
        val (action, actionTag) = if (date == null) "set today" to setTag else "clear" to clearTag
        Text(
            action,
            style = Act.type.meta,
            color = Act.colors.acc.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.testTag(actionTag)
                .semantics { contentDescription = if (date == null) "Set $label date" else "Clear $label date" }
                .clickable(enabled = enabled, role = Role.Button) { if (date == null) picking = true else onPick(null) }
                .padding(vertical = 12.dp),
        )
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

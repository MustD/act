package io.challenge_workshop.mal_ui.animelist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import io.challenge_workshop.mal_ui.theme.Act
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.challenge_workshop.mal_ui.auth.ANIME_LIST_FILTERS_TAG

/**
 * The six mutually exclusive filter choices, in the order they are drawn: the five Watch Statuses,
 * then All.
 *
 * `null` is **All**, and it is the absence of MAL's `status` parameter rather than a sixth value of
 * it: MAL takes one Watch Status or none, which is also why these are chips with one active at a
 * time and not checkboxes.
 *
 * [WatchStatus.Unknown] is deliberately absent. It is where a Watch Status MAL adds later lands, it
 * has no wire value, and offering it would be offering a filter that filters nothing.
 * `AnimeListFiltersTest` holds this list to the enum so a new MAL status cannot be silently
 * unreachable.
 */
val ANIME_LIST_FILTERS: List<WatchStatus?> = listOf(
    WatchStatus.Watching,
    WatchStatus.Completed,
    WatchStatus.OnHold,
    WatchStatus.Dropped,
    WatchStatus.PlanToWatch,
    null,
)

/**
 * What each filter is called on screen.
 *
 * Here rather than in `:core` for the reason [AnimeListSortOrder] gives: `:core` is the tier
 * `:server` also depends on, and display copy is not something a Ktor relay should be carrying.
 */
fun WatchStatus?.filterLabel(): String = when (this) {
    null -> "All"
    WatchStatus.Watching -> "Watching"
    WatchStatus.Completed -> "Completed"
    WatchStatus.OnHold -> "On hold"
    WatchStatus.Dropped -> "Dropped"
    WatchStatus.PlanToWatch -> "Plan to watch"
    // Unreachable through [ANIME_LIST_FILTERS], and named rather than defaulted so a `when` that
    // stops being exhaustive fails the build instead of quietly labelling something "All".
    WatchStatus.Unknown -> "Other"
}

/** The tab's text: MAL's own wire key, and `all` for the absence of one. */
fun WatchStatus?.tabKey(): String = this?.wireValue ?: "all"

/**
 * The prompt row's text: who is signed in, and the slice being listed. `ls` alone for All, which is
 * the whole list rather than a directory of it. [userName] is null until the profile has loaded.
 */
fun promptText(userName: String?, watchStatus: WatchStatus?): String {
    val command = if (watchStatus == null) "ls" else "ls ${watchStatus.tabKey()}/"
    return "${userName ?: "user"}@mal:~$ $command"
}

/**
 * What a filter that matched nothing says.
 *
 * **Deliberately not the same sentence as the empty-account one, and deliberately not built from
 * [filterLabel].** The fix is different — an empty account is something to go and do on
 * myanimelist.net, an empty slice is a filter to undo — and a message that read "Nothing matches
 * Completed" would be about the control rather than about the list. Naming the filter in the words
 * a person would use is the whole of what makes it actionable.
 *
 * Never reached for `null`: All matching nothing *is* the empty account, and there is no filter to
 * name or to undo. That is why this takes a non-null [WatchStatus] rather than the nullable one the
 * row is built from.
 */
fun WatchStatus.emptyListMessage(): String = when (this) {
    WatchStatus.Watching -> "Nothing you are watching right now."
    WatchStatus.Completed -> "Nothing completed."
    WatchStatus.OnHold -> "Nothing on hold."
    WatchStatus.Dropped -> "Nothing dropped."
    WatchStatus.PlanToWatch -> "Nothing you plan to watch."
    // Unreachable through [ANIME_LIST_FILTERS] — it has no wire value, so it cannot be a query —
    // and named rather than defaulted for the same reason [filterLabel] names it.
    WatchStatus.Unknown -> "Nothing under this filter."
}

/**
 * The Watch Status tabs: one at a time, Watching to begin with, drawn as MAL's own wire keys.
 *
 * **A horizontally scrollable [Row]** rather than a fixed tab row, because six tabs do not fit the
 * width of a phone. The active tab is `ink` with a 2dp accent underline and the rest are `dim`; both
 * change over 200ms.
 *
 * [enabled] is false while the replacement first page is in flight. The previously loaded entries
 * stay on screen behind it until the replacement lands, so without this the row would invite a
 * second tap against a list that has not changed yet.
 */
@Composable
fun AnimeListFilters(
    selected: WatchStatus?,
    enabled: Boolean,
    onSelect: (WatchStatus?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ln = Act.colors.ln
    Row(
        modifier = modifier.testTag(ANIME_LIST_FILTERS_TAG)
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = ln, start = Offset(0f, size.height), end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        for (filter in ANIME_LIST_FILTERS) {
            WatchStatusTab(filter.tabKey(), filter == selected, enabled) { onSelect(filter) }
        }
    }
}

@Composable
private fun WatchStatusTab(label: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = Act.colors
    val text by animateColorAsState(if (active) c.ink else c.dim, tween(TAB_FADE_MS), label = "tabText")
    val underline by animateColorAsState(if (active) c.acc else Color.Transparent, tween(TAB_FADE_MS), label = "tabUnderline")
    Box(
        Modifier.height(36.dp)
            .selectable(selected = active, enabled = enabled, role = Role.Tab, onClick = onClick)
            .drawBehind {
                drawRect(underline, Offset(0f, size.height - 2.dp.toPx()), Size(size.width, 2.dp.toPx()))
            }
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Act.type.body, color = text)
    }
}

private const val TAB_FADE_MS = 200
private const val DISABLED_ALPHA = 0.5f

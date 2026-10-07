package io.challenge_workshop.mal_ui.animelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.challenge_workshop.mal_ui.auth.ANIME_LIST_SORT_TAG
import io.challenge_workshop.mal_ui.theme.Act
import io.challenge_workshop.mal_ui.theme.enabledAlpha
import mal_ui.app.shared.generated.resources.Res
import mal_ui.app.shared.generated.resources.swap_vert
import org.jetbrains.compose.resources.painterResource

/**
 * The four orderings MAL supports, in the order they are drawn — the default first.
 *
 * There is no fifth entry, and in particular **no reverse or direction toggle**: MAL's `sort` takes
 * one of four values, each with a fixed direction and no direction parameter, and the Anime List is
 * paged, so reversing what happens to be loaded would reverse a slice rather than the list. See
 * ADR-0003.
 *
 * `anime_id` is a fifth value MAL's documentation marks "under development"; it is absent from
 * [AnimeListSortOrder] itself, which is why it cannot be offered here by accident.
 */
val ANIME_LIST_SORT_ORDERS: List<AnimeListSortOrder> = AnimeListSortOrder.entries.toList()

/**
 * What each Sort Order is called on screen — **field and direction together**, because that is what
 * a Sort Order is (see `CONTEXT.md`) and the direction is not something the user can change.
 *
 * A bare "Score" reads as ascending to about half of everyone, and a list that then opens on a 10
 * looks broken rather than differently ordered. Naming the direction is the only place this can be
 * said, since there is no toggle to say it with.
 *
 * Here rather than in `:core` for the reason [AnimeListSortOrder] gives: `:core` is the tier
 * `:server` also depends on, and display copy is not something a Ktor relay should be carrying.
 */
fun AnimeListSortOrder.sortLabel(): String = when (this) {
    AnimeListSortOrder.LastUpdated -> "Last updated (newest first)"
    AnimeListSortOrder.Score -> "Score (high to low)"
    AnimeListSortOrder.Title -> "Title (A–Z)"
    // **Newest first**, and deliberately not the "oldest first" the ticket's example copy said:
    // MAL's v2 reference marks `anime_start_date` Descending, alongside `list_score` and
    // `list_updated_at`. `anime_title` is the only Ascending one it offers. Still [unverified]
    // against a live account — see `docs/mal-api/anime-list-response.md`, which is where a probe
    // that contradicts this changes it.
    AnimeListSortOrder.StartDate -> "Start date (newest first)"
}

/**
 * The Sort Order row: `[swap_vert] sort: <ordering>`, and a tap moves to the next of the four.
 *
 * A cycle rather than a menu, because there are four orderings and each is a sentence the row already
 * says in full. The ordering is named with its *direction*, since "score" alone reads as ascending to
 * about half of everyone and there is no toggle to say it with.
 *
 * [enabled] is false while the replacement first page is in flight, for the same reason the tabs'
 * is: the entries still on screen are the *previous* ordering's, so a live control would be inviting
 * a second pick against a list that has not changed yet.
 */
@Composable
fun AnimeListSortRow(
    selected: AnimeListSortOrder,
    enabled: Boolean,
    onSelect: (AnimeListSortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Act.colors
    Row(
        modifier.fillMaxWidth().height(36.dp)
            .drawBehind {
                drawLine(c.ln, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            }
            .clickable(enabled = enabled, role = Role.Button) { onSelect(selected.next()) }
            .enabledAlpha(enabled, 0.5f)
            .padding(horizontal = 16.dp)
            .testTag(ANIME_LIST_SORT_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(Res.drawable.swap_vert), contentDescription = null, tint = c.dim, modifier = Modifier.size(16.dp))
        // Written as one string so the row reads as one line of terminal output; the value is the
        // part that changes, so it is the part in `ink`.
        val text = selected.sortRowText()
        val prefix = "sort: "
        Text(
            buildAnnotatedString {
                append(prefix)
                withStyle(SpanStyle(color = c.ink)) { append(text.removePrefix(prefix)) }
            },
            style = Act.type.meta,
            color = c.dim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The sort row's text: the current ordering, lowercased as the terminal style has it. */
fun AnimeListSortOrder.sortRowText(): String = "sort: ${sortLabel().lowercase()}"

/** The ordering a tap on the sort row moves to: the next of the four, wrapping to the first. */
fun AnimeListSortOrder.next(): AnimeListSortOrder = ANIME_LIST_SORT_ORDERS[(ordinal + 1) % ANIME_LIST_SORT_ORDERS.size]

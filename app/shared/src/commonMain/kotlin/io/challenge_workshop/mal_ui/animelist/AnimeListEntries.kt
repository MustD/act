package io.challenge_workshop.mal_ui.animelist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.challenge_workshop.mal_ui.auth.ANIME_LIST_ROW_COLUMN_TAG
import io.challenge_workshop.mal_ui.theme.Act
import io.challenge_workshop.mal_ui.theme.ActMedium
import io.challenge_workshop.mal_ui.theme.ActSmall
import io.challenge_workshop.mal_ui.theme.ContinuousProgress
import io.challenge_workshop.mal_ui.theme.CoverPlaceholder
import io.challenge_workshop.mal_ui.theme.EpisodeCells
import io.challenge_workshop.mal_ui.theme.TickingNumber

/** The narrowest a card may be. The grid divides the window by it, so a phone gets one column. */
val ANIME_CARD_MIN_WIDTH = 320.dp

/**
 * Below this a row or a table line is laid out as on a phone; from here it has room for its extra
 * columns. Measured on the row, not the window: with a panel beside the list the row is narrower.
 */
private val WIDE_MIN_WIDTH = 600.dp

private val CARD_COVER_WIDTH = 72.dp
private val CARD_COVER_HEIGHT = 108.dp
private val ROW_COVER_WIDTH = 36.dp
private val ROW_COVER_HEIGHT = 54.dp

/** The line a table's cells share: a row is never shorter than a thumb. */
private val TABLE_ROW_MIN_HEIGHT = 44.dp

/** The table's column widths, shared by the header and every line so they cannot drift apart. */
private fun tableEpisodesWidth(wide: Boolean) = if (wide) 84.dp else 76.dp
private fun tableScoreWidth(wide: Boolean) = if (wide) 30.dp else 26.dp
private val TABLE_TYPE_WIDTH = 48.dp
private val TABLE_AIRING_WIDTH = 72.dp
private val TABLE_STARTED_WIDTH = 92.dp

/**
 * One List Entry as a card: cover, title, `TV · Finished · sc 8`, the episode number and its cell bar.
 *
 * **Display only.** There is no +1 here and nothing to save: every edit goes through an open Anime
 * Page, so the bottom row is the number and the bar and nothing is reserved beside them.
 */
@Composable
internal fun AnimeListCard(
    entry: AnimeListEntry,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val c = Act.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(if (selected) c.sf2 else c.sf, ActMedium)
            .border(1.dp, if (selected) c.acc else c.ln, ActMedium)
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics { this.selected = selected }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AnimeCoverBox(entry.title, entry.coverUrl(preferLarge = true), Modifier.size(CARD_COVER_WIDTH, CARD_COVER_HEIGHT))
        Column(Modifier.weight(1f).height(CARD_COVER_HEIGHT), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.title, style = Act.type.cardTitle, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(entry.metaLine(), style = Act.type.meta, color = c.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TickingNumber(entry.episodesWatched, Act.type.cardNumber)
                Text(entry.totalLabel(), style = Act.type.meta, color = c.dim)
            }
            EpisodeCells(entry.episodesWatched, entry.totalEpisodes, onPick = null)
        }
    }
}

/**
 * One List Entry as a dense row: a thumbnail, the title, a 3dp progress bar with `7/12` beside it.
 * Wide rows add type and airing, and the score, as columns of their own.
 */
@Composable
internal fun AnimeListRow(
    entry: AnimeListEntry,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val c = Act.colors
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wide = maxWidth >= WIDE_MIN_WIDTH
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth()
                    .background(if (selected) c.sf2 else Color.Transparent)
                    .then(if (selected) Modifier.border(1.dp, c.acc) else Modifier)
                    .clickable(role = Role.Button, onClick = onOpen)
                    .semantics { this.selected = selected }
                    .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimeCoverBox(
                    entry.title,
                    entry.coverUrl(preferLarge = false),
                    Modifier.size(ROW_COVER_WIDTH, ROW_COVER_HEIGHT),
                    letterSize = 15,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.title, style = Act.type.rowTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ContinuousProgress(entry.episodesWatched, entry.totalEpisodes, Modifier.weight(1f).height(3.dp))
                        Text(
                            entry.rowProgress(),
                            style = Act.type.meta.copy(lineHeight = Act.type.tiny.lineHeight),
                            color = c.ink,
                        )
                    }
                }
                if (wide) {
                    Text(
                        entry.detailLabel(),
                        style = Act.type.meta,
                        color = c.dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(112.dp).testTag(ANIME_LIST_ROW_COLUMN_TAG),
                    )
                    Text(
                        entry.tableScore(),
                        style = Act.type.body,
                        color = c.ink,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(28.dp).testTag(ANIME_LIST_ROW_COLUMN_TAG),
                    )
                }
            }
            HorizontalDivider(color = c.ln)
        }
    }
}

/**
 * The header of the table Layout: `ls -l`'s column names, over a dashed rule. Draws the same columns
 * [AnimeListTableRow] does, at the same widths, so the two cannot drift apart.
 */
@Composable
internal fun AnimeListTableHeader(modifier: Modifier = Modifier) {
    val c = Act.colors
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wide = maxWidth >= WIDE_MIN_WIDTH
        val style = Act.type.tiny.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)
        Row(
            Modifier.fillMaxWidth()
                .drawBehind {
                    val y = size.height - 0.5.dp.toPx()
                    drawLine(
                        c.ln,
                        Offset(0f, y),
                        Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                    )
                }
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TableHeaderCell("EP", Modifier.width(tableEpisodesWidth(wide)), style)
            TableHeaderCell("SC", Modifier.width(tableScoreWidth(wide)), style)
            TableHeaderCell("TITLE", Modifier.weight(1f), style)
            if (wide) {
                TableHeaderCell("TYPE", Modifier.width(TABLE_TYPE_WIDTH), style)
                TableHeaderCell("AIRING", Modifier.width(TABLE_AIRING_WIDTH), style)
                TableHeaderCell("STARTED", Modifier.width(TABLE_STARTED_WIDTH), style)
            }
        }
    }
}

@Composable
private fun TableHeaderCell(text: String, modifier: Modifier, style: TextStyle) {
    Text(text, style = style, color = Act.colors.dim, maxLines = 1, modifier = modifier)
}

/**
 * One List Entry as an `ls -l` line: episodes, score, title; wide adds type, airing and started.
 *
 * Started is **empty** for now: the list endpoint is not asked for dates, so a row carries none, and
 * the column keeps its width so it fills in without moving anything when a date becomes known.
 * There is no `[+]` column — the table is display only, like the other two.
 */
@Composable
internal fun AnimeListTableRow(
    entry: AnimeListEntry,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val c = Act.colors
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wide = maxWidth >= WIDE_MIN_WIDTH
        val cell = Act.type.body.copy(fontWeight = FontWeight.Normal)
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = TABLE_ROW_MIN_HEIGHT)
                    .background(if (selected) c.sf2 else Color.Transparent)
                    .then(if (selected) Modifier.border(1.dp, c.acc) else Modifier)
                    .clickable(role = Role.Button, onClick = onOpen)
                    .semantics { this.selected = selected }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(entry.tableEpisodes(), style = Act.type.body, color = c.acc, maxLines = 1, modifier = Modifier.width(tableEpisodesWidth(wide)))
                Text(entry.tableScore(), style = cell, color = c.dim, maxLines = 1, modifier = Modifier.width(tableScoreWidth(wide)))
                Text(entry.title, style = cell, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (wide) {
                    TableColumn(entry.mediaTypeLabel().orEmpty(), TABLE_TYPE_WIDTH)
                    TableColumn(entry.airingStatus.airingLabel()?.lowercase().orEmpty(), TABLE_AIRING_WIDTH)
                    TableColumn("", TABLE_STARTED_WIDTH)
                }
            }
            HorizontalDivider(color = c.ln)
        }
    }
}

@Composable
private fun TableColumn(text: String, width: Dp) {
    Text(
        text,
        style = Act.type.body.copy(fontWeight = FontWeight.Normal),
        color = Act.colors.dim,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width).testTag(ANIME_LIST_ROW_COLUMN_TAG),
    )
}

/**
 * The cover art, over the [CoverPlaceholder] that is always there.
 *
 * **The placeholder is drawn first and the image over it, which is the whole error handling.** An
 * entry MAL sent no `main_picture` for never builds an [AsyncImage] at all; one whose art fails to
 * load draws nothing over the placeholder, and Coil neither throws nor propagates that failure — so
 * a broken cover costs its own card and takes the row, the grid and the scroll with it nowhere.
 *
 * **Nothing here looks at a status code, and nothing can.** On the web Target a missing image is a
 * network-level CORS failure with no status at all — the CDN sends `Access-Control-*` headers on a
 * 200 and none on a 404 — while jvm and android see a plain 404. A branch on "was it a 404" would
 * therefore be right on two Targets and silently wrong on the third; the placeholder-behind is
 * the same on all three.
 *
 * `contentDescription` is null on purpose: the title is drawn beside the art in every Layout, so the
 * cover is decorative and a screen reader announcing it would read the title twice.
 */
@Composable
internal fun AnimeCoverBox(title: String, url: String?, modifier: Modifier = Modifier, letterSize: Int = 26) {
    Box(modifier.clip(ActSmall)) {
        CoverPlaceholder(title, Modifier.fillMaxSize(), letterSize)
        url?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The colour behind every skeleton, and behind the bars an Anime Page draws while it loads.
 *
 * One function rather than a literal at each site: the skeleton is a promise about the shape the
 * real content arrives in, and a different grey from the one it is replaced by makes the arrival
 * look like a failure.
 */
@Composable
internal fun placeholderColor(): Color = Act.colors.ln

/** [AnimeListCard]'s shape with nothing in it. See `AnimeListSection`'s note on skeletons. */
@Composable
internal fun AnimeCardSkeleton(modifier: Modifier = Modifier) {
    val c = Act.colors
    Row(
        modifier.fillMaxWidth().background(c.sf, ActMedium).border(1.dp, c.ln, ActMedium).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(CARD_COVER_WIDTH, CARD_COVER_HEIGHT).clip(ActSmall).background(placeholderColor()))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonBar(Modifier.fillMaxWidth())
            SkeletonBar(Modifier.fillMaxWidth(0.6f))
        }
    }
}

/** [AnimeListRow]'s shape with nothing in it. */
@Composable
internal fun AnimeRowSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(ROW_COVER_WIDTH, ROW_COVER_HEIGHT).clip(ActSmall).background(placeholderColor()))
        SkeletonBar(Modifier.weight(1f))
        SkeletonBar(Modifier.width(48.dp))
    }
}

/** [AnimeListTableRow]'s shape with nothing in it. */
@Composable
internal fun AnimeTableSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = TABLE_ROW_MIN_HEIGHT).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SkeletonBar(Modifier.width(tableEpisodesWidth(wide = false)))
        SkeletonBar(Modifier.weight(1f))
    }
}

@Composable
private fun SkeletonBar(modifier: Modifier = Modifier) {
    Box(modifier.height(12.dp).background(placeholderColor(), ActSmall))
}

/**
 * Which of MAL's two cover-art sizes to ask the CDN for.
 *
 * A card asks for `large` and a dense row for `medium`, falling back to whichever one is present:
 * MAL omits fields it has no value for, so `main_picture` can be absent altogether, present with
 * only one size, or — on an entry whose art was never uploaded — present with an empty string in it.
 * All three arrive here as null and render the placeholder.
 */
internal fun AnimeListEntry.coverUrl(preferLarge: Boolean): String? = picture.coverUrl(preferLarge)

internal fun AnimePicture?.coverUrl(preferLarge: Boolean): String? {
    val picture = this ?: return null
    val preferred = if (preferLarge) picture.large else picture.medium
    val fallback = if (preferLarge) picture.medium else picture.large
    return (preferred ?: fallback)?.takeIf { it.isNotBlank() }
}

/**
 * The card's one meta line: media type, Airing Status and the user's score, `TV · Finished · sc 8`.
 *
 * Joined from a list that drops its nulls, so an entry MAL sent no `media_type` for closes up rather
 * than showing a gap with two separators around it. The Watch Status is not here: it is the filter
 * the whole list is under, and the tab already says it.
 */
internal fun AnimeListEntry.metaLine(): String =
    listOfNotNull(mediaTypeLabel(), airingStatus.airingLabel(), scoreTag()).joinToString(PART_SEPARATOR)

/**
 * The user's own score, short. MAL sends `0` both for "not scored" and for a score of zero and does
 * not distinguish them — its own scale starts at 1 — so `0` is read as unscored.
 */
internal fun AnimeListEntry.scoreTag(): String = if (score in 1..10) "sc $score" else "unscored"

/** The score as a table cell: two digits, or `--`. */
internal fun AnimeListEntry.tableScore(): String = if (score in 1..10) score.toString().padStart(2, '0') else "--"

/**
 * The episode total after the number, `/24`.
 *
 * MAL reports `0` for a total it does not know — a currently-airing show whose run is unannounced —
 * rather than omitting it, and "3/0" reads as a bug. So an unknown total shows as `?`.
 */
internal fun AnimeListEntry.totalLabel(): String = "/" + if (totalEpisodes > 0) totalEpisodes.toString() else "?"

/** Watched-of-total on a dense row, `7/12`. */
internal fun AnimeListEntry.rowProgress(): String = "$episodesWatched${totalLabel()}"

/** Watched-of-total in the table, `019/028`, or `003/???` for an unknown total. */
internal fun AnimeListEntry.tableEpisodes(): String =
    episodesWatched.toString().padStart(3, '0') + "/" +
        if (totalEpisodes > 0) totalEpisodes.toString().padStart(3, '0') else "???"

/** Media type and Airing Status, for a wide row's column. */
internal fun AnimeListEntry.detailLabel(): String =
    listOfNotNull(mediaTypeLabel(), airingStatus.airingLabel()).joinToString(PART_SEPARATOR)

private const val PART_SEPARATOR: String = " · "

/**
 * MAL's `media_type`, in the spelling a person uses.
 *
 * A `when` over a **string**, with the raw value tidied up as the fallback, because `media_type` is
 * left a string in `:core` on purpose: MAL adds types, and an entry whose type is one this build has
 * never heard of should read as "Music Video", not vanish and not crash.
 */
internal fun AnimeListEntry.mediaTypeLabel(): String? = mediaTypeLabel(mediaType)

internal fun mediaTypeLabel(mediaType: String?): String? = when (val type = mediaType?.lowercase()) {
    null, "" -> null
    "tv" -> "TV"
    "ova" -> "OVA"
    "ona" -> "ONA"
    "movie" -> "Movie"
    "special" -> "Special"
    "music" -> "Music"
    "unknown" -> null
    else -> type.split('_').joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
    }
}

/**
 * What the anime itself is doing, in three words at most.
 *
 * Null for [AiringStatus.Unknown] — the entry MAL sent a status this build has never seen for —
 * because "Unknown" on a card is a word that tells the reader nothing and takes the room the media
 * type needed.
 */
internal fun AiringStatus.airingLabel(): String? = when (this) {
    AiringStatus.CurrentlyAiring -> "Airing"
    AiringStatus.FinishedAiring -> "Finished"
    AiringStatus.NotYetAired -> "Not yet aired"
    AiringStatus.Unknown -> null
}

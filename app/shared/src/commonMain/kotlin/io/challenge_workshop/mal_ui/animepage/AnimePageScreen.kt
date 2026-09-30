package io.challenge_workshop.mal_ui.animepage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.challenge_workshop.mal_ui.animelist.AnimeCoverBox
import io.challenge_workshop.mal_ui.animelist.airingLabel
import io.challenge_workshop.mal_ui.animelist.coverUrl
import io.challenge_workshop.mal_ui.animelist.filterLabel
import io.challenge_workshop.mal_ui.animelist.mediaTypeLabel
import io.challenge_workshop.mal_ui.animelist.placeholderColor
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_BACK_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_CLOSE_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_ERROR_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_LIST_STATUS_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_LOADING_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_SYNOPSIS_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_TAG
import io.challenge_workshop.mal_ui.auth.AnimePageActions
import io.challenge_workshop.mal_ui.auth.ErrorCard
import io.challenge_workshop.mal_ui.auth.LabelledValue

/**
 * One Anime Page, read-only.
 *
 * Takes the [AnimePage] as a value and its actions as a record, like every screen here. What it *is*
 * — loading, loaded, failed — is [AnimePage.load], decided in `:core`; this is a `when` over it.
 * The List Entry fields are shown either way, from whatever the page opened with until the fetch
 * replaces them, and **nothing is editable yet**: editing waits for a fetch that has succeeded
 * (spec, "No editing until that fetch has succeeded").
 *
 * **Back is handled by hand**, per ADR-0006: [BackHandler] for the Android back gesture, and Esc for
 * a keyboard, which no back dispatcher maps on desktop. Both go one page back; ✕ closes them all.
 * [canGoBack] is whether there is a page to go back *to* — with one page, ← would be a second ✕.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AnimePageScreen(
    page: AnimePage,
    canGoBack: Boolean,
    actions: AnimePageActions,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = actions.onBack)

    // Esc needs focus to be heard, so the page takes it as it opens.
    val focus = remember { FocusRequester() }
    LaunchedEffect(page.animeId) { runCatching { focus.requestFocus() } }

    // The caller's modifier carries the signed-in screen's tag, and a second `testTag` on the same
    // node is ignored, so the page's own goes on a child.
    Box(modifier) {
        PageContent(page, canGoBack, actions, focus)
    }
}

@Composable
private fun PageContent(page: AnimePage, canGoBack: Boolean, actions: AnimePageActions, focus: FocusRequester) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ANIME_PAGE_TAG)
            .focusRequester(focus)
            .focusTarget()
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                    actions.onBack()
                    true
                } else {
                    false
                }
            },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (canGoBack) {
                TextButton(
                    onClick = actions.onBack,
                    modifier = Modifier.testTag(ANIME_PAGE_BACK_TAG).semantics { contentDescription = "Back" },
                ) { Text("←") }
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = actions.onClose,
                modifier = Modifier.testTag(ANIME_PAGE_CLOSE_TAG).semantics { contentDescription = "Close" },
            ) { Text("✕") }
        }
        if (page.load == AnimePageLoad.Loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().testTag(ANIME_PAGE_LOADING_TAG))
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(page)
            HorizontalDivider()
            ListStatusSection(page)
            HorizontalDivider()
            SynopsisSection(page, actions.onRetry)
        }
    }
}

@Composable
private fun Header(page: AnimePage) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        AnimeCoverBox(
            title = page.title,
            url = page.picture.coverUrl(preferLarge = true),
            modifier = Modifier.width(COVER_WIDTH).height(COVER_WIDTH * 3 / 2),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(page.title, style = MaterialTheme.typography.headlineSmall)
            val facts = listOfNotNull(
                mediaTypeLabel(page.mediaType),
                page.airingStatus.airingLabel(),
                if (page.totalEpisodes > 0) "${page.totalEpisodes} episodes" else null,
            ).joinToString(" · ")
            if (facts.isNotEmpty()) {
                Text(
                    facts,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The user's List Entry, read-only. Absent for an anime that is not on their list: the section says
 * so, and that is all it says.
 *
 * A date MAL sent partial (`2024`, `2024-03`) is shown as it is. One that is unset — or not fetched
 * yet, which the row it opened from never carries — is a dash.
 */
@Composable
private fun ListStatusSection(page: AnimePage) {
    Column(
        Modifier.fillMaxWidth().testTag(ANIME_PAGE_LIST_STATUS_TAG),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Your list entry", style = MaterialTheme.typography.titleMedium)
        val status = page.listStatus
        if (status == null) {
            Text(
                "Not on your list.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LabelledValue("Status", status.watchStatus.filterLabel())
            LabelledValue(
                "Episodes",
                "${status.episodesWatched} / ${if (page.totalEpisodes > 0) page.totalEpisodes.toString() else "?"}",
            )
            LabelledValue("Score", if (status.score in 1..10) status.score.toString() else "No score")
            LabelledValue("Started", status.startDate ?: NO_DATE)
            LabelledValue("Finished", status.finishDate ?: NO_DATE)
        }
    }
}

@Composable
private fun SynopsisSection(page: AnimePage, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Synopsis", style = MaterialTheme.typography.titleMedium)
        when (val load = page.load) {
            AnimePageLoad.Loading -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) {
                    Spacer(
                        Modifier.fillMaxWidth().height(12.dp)
                            .background(placeholderColor(), RoundedCornerShape(4.dp)),
                    )
                }
            }

            AnimePageLoad.Loaded -> Text(
                page.synopsis?.takeIf { it.isNotBlank() } ?: "No synopsis.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(ANIME_PAGE_SYNOPSIS_TAG),
            )

            is AnimePageLoad.Failed -> Column(
                Modifier.testTag(ANIME_PAGE_ERROR_TAG),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ErrorCard("Could not load this anime", load.message)
                OutlinedButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

private val COVER_WIDTH = 120.dp
private const val NO_DATE = "—"

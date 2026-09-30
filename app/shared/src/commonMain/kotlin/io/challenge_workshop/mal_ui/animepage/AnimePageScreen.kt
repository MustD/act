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
import io.challenge_workshop.mal_ui.animelist.Anime
import io.challenge_workshop.mal_ui.animelist.AnimeCoverBox
import io.challenge_workshop.mal_ui.animelist.airingLabel
import io.challenge_workshop.mal_ui.animelist.coverUrl
import io.challenge_workshop.mal_ui.animelist.mediaTypeLabel
import io.challenge_workshop.mal_ui.animelist.placeholderColor
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_BACK_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_CLOSE_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_ERROR_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_LOADING_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_SYNOPSIS_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_TAG
import io.challenge_workshop.mal_ui.auth.AnimePageActions
import io.challenge_workshop.mal_ui.auth.ErrorCard

/**
 * One Anime Page.
 *
 * Takes the [AnimePage] as a value and its actions as a record, like every screen here. What it *is*
 * — loading, loaded, failed — is [AnimePage.load], decided in `:core`; this is a `when` over it.
 * The List Entry fields are shown either way, from whatever the page opened with until the fetch
 * replaces them, and **nothing is editable until the fetch has succeeded** ([AnimePage.canEdit]): its dates decide
 * whether an automatic rule may fill them, and the row may be stale.
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
            Header(page.anime)
            HorizontalDivider()
            ListEntrySection(page, actions.onEdit, actions.onAdd)
            HorizontalDivider()
            SynopsisSection(page, actions.onRetry)
            RelatedSection(page.related, actions.onOpenRelated)
        }
    }
}

@Composable
private fun Header(anime: Anime) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        AnimeCoverBox(
            title = anime.title,
            url = anime.picture.coverUrl(preferLarge = true),
            modifier = Modifier.width(COVER_WIDTH).height(COVER_WIDTH * 3 / 2),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(anime.title, style = MaterialTheme.typography.headlineSmall)
            val facts = listOfNotNull(
                mediaTypeLabel(anime.mediaType),
                anime.airingStatus.airingLabel(),
                if (anime.totalEpisodes > 0) "${anime.totalEpisodes} episodes" else null,
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

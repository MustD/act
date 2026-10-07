package io.challenge_workshop.mal_ui.animepage

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_SYNOPSIS_TOGGLE_TAG
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import io.challenge_workshop.mal_ui.theme.Act
import io.challenge_workshop.mal_ui.theme.ActSmall
import io.challenge_workshop.mal_ui.theme.SectionLabel
import mal_ui.app.shared.generated.resources.Res
import mal_ui.app.shared.generated.resources.arrow_back
import mal_ui.app.shared.generated.resources.close
import mal_ui.app.shared.generated.resources.open_in_new
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_BACK_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_CLOSE_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_ERROR_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_LOADING_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_OPEN_TAG
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_PATH_TAG
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
 * [canGoBack] is whether there is a page to go back *to*. On a phone ← is always there and, with one
 * page, closes it; in the [sidePanel] ✕ closes everything and ← appears only with a page to go back to.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AnimePageScreen(
    page: AnimePage,
    canGoBack: Boolean,
    actions: AnimePageActions,
    modifier: Modifier = Modifier,
    sidePanel: Boolean = false,
    /** False while the page is only being slid away, so a back then is not taken for one more page. */
    open: Boolean = true,
) {
    BackHandler(enabled = open, onBack = actions.onBack)

    // Esc needs focus to be heard, so the page takes it as it opens.
    val focus = remember { FocusRequester() }
    LaunchedEffect(page.animeId) { runCatching { focus.requestFocus() } }

    // The caller's modifier carries the signed-in screen's tag, and a second `testTag` on the same
    // node is ignored, so the page's own goes on a child.
    Box(modifier) {
        PageContent(page, canGoBack, sidePanel, actions, focus)
    }
}

@Composable
private fun PageContent(
    page: AnimePage,
    canGoBack: Boolean,
    sidePanel: Boolean,
    actions: AnimePageActions,
    focus: FocusRequester,
) {
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
        TopBar(page, canGoBack, sidePanel, actions)
        if (page.load == AnimePageLoad.Loading) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().testTag(ANIME_PAGE_LOADING_TAG),
                color = Act.colors.acc,
                trackColor = Act.colors.ln,
            )
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Header(page.anime)
            ListEntrySection(page, actions.onEdit, actions.onAdd)
            SynopsisSection(page, sidePanel, actions.onRetry)
            RelatedSection(page.related, actions.onOpenRelated)
        }
    }
}

/** 52dp: ← (✕ in the side panel), the page's path, and the page on myanimelist.net. */
@Composable
private fun TopBar(page: AnimePage, canGoBack: Boolean, sidePanel: Boolean, actions: AnimePageActions) {
    val uriHandler = LocalUriHandler.current
    Column {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!sidePanel || canGoBack) {
                TopBarIcon(Res.drawable.arrow_back, "Back", actions.onBack, Modifier.testTag(ANIME_PAGE_BACK_TAG))
            }
            if (sidePanel) {
                TopBarIcon(Res.drawable.close, "Close", actions.onClose, Modifier.testTag(ANIME_PAGE_CLOSE_TAG))
            }
            Text(
                animePagePath(page.shownListEntry?.watchStatus, page.anime.title),
                style = Act.type.meta,
                color = Act.colors.dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp).testTag(ANIME_PAGE_PATH_TAG),
            )
            TopBarIcon(
                Res.drawable.open_in_new,
                "Open on myanimelist.net",
                { uriHandler.openUri("https://myanimelist.net/anime/${page.animeId}") },
                Modifier.testTag(ANIME_PAGE_OPEN_TAG),
            )
        }
        HorizontalDivider(color = Act.colors.ln)
    }
}

@Composable
private fun TopBarIcon(icon: DrawableResource, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(44.dp).clip(ActSmall).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Act.colors.ink, modifier = Modifier.size(22.dp))
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
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val tags = listOfNotNull(mediaTypeLabel(anime.mediaType), anime.airingStatus.airingLabel())
            if (tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (tag in tags) {
                        Text(
                            tag.uppercase(),
                            style = Act.type.tag,
                            color = Act.colors.dim,
                            modifier = Modifier.border(1.dp, Act.colors.ln, ActSmall).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(anime.title, style = Act.type.pageTitle, color = Act.colors.ink)
            Text(
                if (anime.totalEpisodes > 0) "${anime.totalEpisodes} episodes" else "episode count unknown",
                style = Act.type.meta,
                color = Act.colors.dim,
            )
        }
    }
}

@Composable
private fun SynopsisSection(page: AnimePage, sidePanel: Boolean, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("synopsis")
        when (val load = page.load) {
            AnimePageLoad.Loading -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) {
                    Spacer(
                        Modifier.fillMaxWidth().height(12.dp)
                            .background(Act.colors.sf2, RoundedCornerShape(2.dp)),
                    )
                }
            }

            AnimePageLoad.Loaded -> SynopsisText(page.synopsis?.takeIf { it.isNotBlank() } ?: "No synopsis.", sidePanel)

            is AnimePageLoad.Failed -> Column(
                Modifier.testTag(ANIME_PAGE_ERROR_TAG),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ErrorCard("Could not load this anime", load.message)
                OutlinedButton(
                    onClick = onRetry,
                    shape = ActSmall,
                    border = BorderStroke(1.dp, Act.colors.ln),
                ) { Text("Retry", style = Act.type.body, color = Act.colors.acc) }
            }
        }
    }
}

private val COVER_WIDTH = 100.dp

private const val SYNOPSIS_CLAMP_LINES = 3

/**
 * The synopsis: in full in the side panel, clamped to [SYNOPSIS_CLAMP_LINES] lines on a phone with a
 * `more ▾` / `less ▴` toggle that is offered only when the text really is cut off.
 */
@Composable
private fun SynopsisText(text: String, sidePanel: Boolean) {
    var expanded by remember(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    val clamped = !sidePanel && !expanded
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text,
            style = Act.type.synopsis,
            color = Act.colors.ink,
            maxLines = if (clamped) SYNOPSIS_CLAMP_LINES else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (clamped && it.hasVisualOverflow) overflows = true },
            modifier = Modifier.testTag(ANIME_PAGE_SYNOPSIS_TAG),
        )
        if (!sidePanel && overflows) {
            Text(
                if (expanded) "less ▴" else "more ▾",
                style = Act.type.meta,
                color = Act.colors.acc,
                modifier = Modifier.testTag(ANIME_PAGE_SYNOPSIS_TOGGLE_TAG)
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(vertical = 8.dp),
            )
        }
    }
}

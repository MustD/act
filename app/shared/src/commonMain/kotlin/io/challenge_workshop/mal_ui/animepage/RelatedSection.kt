package io.challenge_workshop.mal_ui.animepage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.challenge_workshop.mal_ui.animelist.AnimeCoverBox
import io.challenge_workshop.mal_ui.animelist.coverUrl
import io.challenge_workshop.mal_ui.auth.ANIME_PAGE_RELATED_TAG
import io.challenge_workshop.mal_ui.auth.animePageRelatedOnListTag
import io.challenge_workshop.mal_ui.auth.animePageRelatedTag

/**
 * The Related Anime, in MAL's order: cover, title, relation, and a mark if the user has it on their
 * list. Each row opens its own Anime Page on top of this one. Nothing is drawn for an anime with none.
 */
@Composable
internal fun RelatedSection(related: List<RelatedAnime>, onOpen: (RelatedAnime) -> Unit) {
    if (related.isEmpty()) return
    HorizontalDivider()
    Column(Modifier.fillMaxWidth().testTag(ANIME_PAGE_RELATED_TAG), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Related anime", style = MaterialTheme.typography.titleMedium)
        related.forEach { anime ->
            Row(
                Modifier.fillMaxWidth()
                    .clickable { onOpen(anime) }
                    .padding(vertical = 4.dp)
                    .testTag(animePageRelatedTag(anime.animeId)),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimeCoverBox(
                    title = anime.title,
                    url = anime.picture.coverUrl(preferLarge = false),
                    modifier = Modifier.width(RELATED_COVER_WIDTH).height(RELATED_COVER_WIDTH * 3 / 2),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(anime.title, style = MaterialTheme.typography.bodyLarge)
                    if (anime.relation.isNotEmpty()) {
                        Text(
                            anime.relation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (anime.onList) {
                    Text(
                        "On your list",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag(animePageRelatedOnListTag(anime.animeId)),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private val RELATED_COVER_WIDTH = 48.dp

package net.io_workshop.act.animepage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.io_workshop.act.animelist.AnimeCoverBox
import net.io_workshop.act.animelist.coverUrl
import net.io_workshop.act.auth.ANIME_PAGE_RELATED_TAG
import net.io_workshop.act.auth.animePageRelatedOnListTag
import net.io_workshop.act.auth.animePageRelatedTag
import net.io_workshop.act.theme.Act
import net.io_workshop.act.theme.SectionLabel
import net.io_workshop.act.theme.wheelHorizontalScroll

/**
 * `// related`: the Related Anime, in MAL's order, as a horizontal scroller of 96dp tiles — cover,
 * the relation, the title, and `● on list` where the user has it. Each tile opens its own Anime Page
 * on top of this one. Nothing is drawn for an anime with none.
 */
@Composable
internal fun RelatedSection(related: List<RelatedAnime>, onOpen: (RelatedAnime) -> Unit) {
    if (related.isEmpty()) return
    Column(Modifier.fillMaxWidth().testTag(ANIME_PAGE_RELATED_TAG), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("related")
        val scroll = rememberScrollState()
        Row(Modifier.wheelHorizontalScroll(scroll).horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            related.forEach { anime ->
                Column(
                    Modifier.width(RELATED_TILE_WIDTH)
                        .clickable(role = Role.Button) { onOpen(anime) }
                        .testTag(animePageRelatedTag(anime.animeId)),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AnimeCoverBox(
                        title = anime.title,
                        url = anime.picture.coverUrl(preferLarge = false),
                        adult = anime.adultCover,
                        modifier = Modifier.width(RELATED_TILE_WIDTH).height(RELATED_TILE_WIDTH * 3 / 2),
                    )
                    if (anime.relation.isNotEmpty()) {
                        Text(anime.relation.uppercase(), style = Act.type.tag, color = Act.colors.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(anime.title, style = Act.type.rowTitle.copy(fontSize = 12.sp, lineHeight = 16.sp), color = Act.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (anime.onList) {
                        Text("● on list", style = Act.type.tag, color = Act.colors.acc, modifier = Modifier.testTag(animePageRelatedOnListTag(anime.animeId)))
                    }
                }
            }
        }
    }
}

private val RELATED_TILE_WIDTH = 96.dp

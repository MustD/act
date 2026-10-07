package io.challenge_workshop.mal_ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.challenge_workshop.mal_ui.animepage.SaveLog
import io.challenge_workshop.mal_ui.animepage.SaveOutcome
import io.challenge_workshop.mal_ui.animepage.logDescription
import io.challenge_workshop.mal_ui.animepage.logResult
import io.challenge_workshop.mal_ui.theme.Act
import io.challenge_workshop.mal_ui.theme.rememberSpinnerGlyph

internal const val KEY_HINTS = "j/k select · +/− episode · 1–0 score · [ ] status · esc close"

/**
 * The 28dp bar that says what the last save did and whether one is still going. Fed from the save
 * loop alone, so it never mentions a GET. Empty before the session's first save.
 *
 * [keyHints] is wide-only and keyboard-only: the caller decides, since both are about the window.
 */
@Composable
internal fun LogBar(log: SaveLog, keyHints: Boolean, modifier: Modifier = Modifier) {
    val c = Act.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(c.sf)
            .drawBehind { drawLine(c.ln, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            .padding(horizontal = 14.dp)
            .testTag(LOG_BAR_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val last = log.last
        Text(
            last?.let(::logDescription).orEmpty(),
            style = Act.type.meta,
            color = c.dim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag(LOG_BAR_TEXT_TAG),
        )
        if (keyHints) {
            Text(KEY_HINTS, style = Act.type.meta, color = c.dim, maxLines = 1, modifier = Modifier.testTag(LOG_BAR_HINTS_TAG))
        }
        when {
            log.pending -> Text("${rememberSpinnerGlyph()}", style = Act.type.meta, color = c.pend, modifier = Modifier.testTag(LOG_BAR_RESULT_TAG))
            last != null -> {
                val refused = last.outcome is SaveOutcome.Refused
                logResult(last.outcome)?.let {
                    Text(
                        it,
                        style = Act.type.meta,
                        color = if (refused) c.error else c.acc,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 280.dp).testTag(LOG_BAR_RESULT_TAG),
                    )
                }
            }
        }
    }
}

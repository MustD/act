package io.challenge_workshop.mal_ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private const val SPINNER = "⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏"

/** "// episodes" section header, with an optional trailing slot (the saving indicator). */
@Composable
fun SectionLabel(text: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("// " + text.uppercase(), style = Act.type.sectionLabel, color = Act.colors.dim)
        trailing()
    }
}

/** Braille spinner + "saving", in the pending colour. Draws nothing when idle. */
@Composable
fun SavingIndicator(saving: Boolean, modifier: Modifier = Modifier) {
    if (!saving) return
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(90); frame = (frame + 1) % SPINNER.length } }
    Text("${SPINNER[frame]} saving", style = Act.type.tiny.copy(fontWeight = FontWeight.Medium), color = Act.colors.pend, modifier = modifier)
}

/** Placeholder behind cover art: 135° stripes + first letter. Put the Coil AsyncImage over it, as today. */
@Composable
fun CoverPlaceholder(title: String, modifier: Modifier = Modifier, letterSize: Int = 26) {
    val c = Act.colors
    Box(
        modifier.clip(ActSmall).background(c.sf2).drawBehind {
            val step = 7.dp.toPx()
            var x = -size.height
            while (x < size.width) {
                drawLine(c.ln, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.dp.toPx())
                x += step
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        title.firstOrNull()?.let {
            Text(it.uppercase(), style = Act.type.rowTitle.copy(fontSize = letterSize.sp), color = c.dim)
        }
    }
}

/** Zero-padded number that rolls in from above whenever it changes. */
@Composable
fun TickingNumber(value: Int, style: TextStyle) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            (slideInVertically(tween(280, easing = ActEasing)) { -(it * 0.4f).toInt() } + fadeIn(tween(280))) togetherWith fadeOut(tween(100))
        },
        label = "episodes",
    ) { Text(it.toString().padStart(2, '0'), style = style, color = Act.colors.ink) }
}

/** Animated fill colour with a left-to-right stagger. */
@Composable
private fun cellColor(filled: Boolean, index: Int, staggerMs: Int, durationMs: Int, on: Color, off: Color): Color {
    val color by animateColorAsState(
        if (filled) on else off,
        animationSpec = tween(durationMs, delayMillis = index * staggerMs),
        label = "cell",
    )
    return color
}

/**
 * One cell per episode. Interactive on the Anime Page (tap N → watched = N; tap the current last → N−1),
 * passive on list cards (pass onPick = null, cellHeight = 6.dp, flexible width).
 * Falls back to [ContinuousProgress] when the total is unknown (0) or above [maxCells].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpisodeCells(
    watched: Int,
    total: Int,
    onPick: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
    maxCells: Int = if (onPick != null) 60 else 30,
    cellHeight: Dp = if (onPick != null) 22.dp else 6.dp,
) {
    if (total <= 0 || total > maxCells) {
        ContinuousProgress(watched, total, modifier.height(if (onPick != null) 8.dp else 6.dp))
        return
    }
    val c = Act.colors
    if (onPick == null) {
        Row(modifier.fillMaxWidth().height(cellHeight), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(total) { i ->
                Box(Modifier.weight(1f).fillMaxHeight().background(cellColor(i < watched, i, 8, 300, c.acc, c.ln)))
            }
        }
    } else {
        val w = if (total > 30) 12.dp else 18.dp
        FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(total) { i ->
                Box(
                    Modifier.size(w, cellHeight).clip(RoundedCornerShape(1.dp))
                        .background(cellColor(i < watched, i, 10, 250, c.acc, c.ln))
                        .clickable { onPick(if (i + 1 == watched) i else i + 1) }
                        .semantics { contentDescription = "Episode ${i + 1}" },
                )
            }
        }
    }
}

@Composable
fun ContinuousProgress(watched: Int, total: Int, modifier: Modifier = Modifier) {
    val c = Act.colors
    val target = if (total > 0) (watched.toFloat() / total).coerceIn(0f, 1f) else 1f
    val frac by animateFloatAsState(target, tween(350, easing = ActEasing), label = "progress")
    Box(modifier.fillMaxWidth().background(c.ln)) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(frac).drawBehind {
                if (total > 0) drawRect(c.acc)
                else { // unknown total: dashed bar
                    var x = 0f; val d = 6.dp.toPx(); val g = 4.dp.toPx()
                    while (x < size.width) { drawRect(c.acc, Offset(x, 0f), Size(d, size.height)); x += d + g }
                }
            },
        )
    }
}

/** 1…10 cells; cells ≤ score filled. */
@Composable
fun ScoreCells(score: Int, enabled: Boolean, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Act.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (n in 1..10) {
            val on = n <= score
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(1.dp))
                    .background(cellColor(on, n - 1, 18, 200, c.acc, c.sf2))
                    .clickable(enabled = enabled && n != score) { onPick(n) },
                contentAlignment = Alignment.Center,
            ) { Text("$n", style = Act.type.body, color = if (on) c.onAcc else c.dim, textAlign = TextAlign.Center) }
        }
    }
}

/** "● watching ○ completed …" — five Watch Statuses by wire key. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> WatchStatusChips(options: List<T>, selected: T, key: (T) -> String, enabled: Boolean, onPick: (T) -> Unit) {
    val c = Act.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (o in options) {
            val on = o == selected
            val border by animateColorAsState(if (on) c.acc else c.ln, label = "chipBorder")
            Box(
                Modifier.height(40.dp).clip(ActSmall).background(if (on) c.sf else Color.Transparent)
                    .border(1.dp, border, ActSmall)
                    .clickable(enabled = enabled && !on) { onPick(o) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("${if (on) "●" else "○"} ${key(o)}", style = Act.type.body, color = if (on) c.acc else c.ink) }
        }
    }
}

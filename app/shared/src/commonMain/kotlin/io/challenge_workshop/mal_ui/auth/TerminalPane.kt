package io.challenge_workshop.mal_ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.challenge_workshop.mal_ui.theme.Act
import io.challenge_workshop.mal_ui.theme.ActMedium
import io.challenge_workshop.mal_ui.theme.ActSmall
import io.challenge_workshop.mal_ui.theme.SectionLabel

/** `ACT_`: the wordmark, with the accent underscore, at the size the sidebar uses unless told otherwise. */
@Composable
internal fun ActWordmark(modifier: Modifier = Modifier, size: Int = 22) {
    Text(
        buildAnnotatedString {
            append("ACT")
            withStyle(SpanStyle(color = Act.colors.acc)) { append("_") }
        },
        style = Act.type.sheetTitle.copy(fontFamily = Act.type.meta.fontFamily, fontSize = size.sp, lineHeight = (size + 6).sp),
        color = Act.colors.ink,
        modifier = modifier,
    )
}

/** `guest@mal:~$ <command>▌` — the prompt the signed-out screens speak in, with the same blinking cursor. */
@Composable
internal fun PromptLine(command: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("guest@mal:~$ $command", style = Act.type.meta, color = Act.colors.dim, modifier = Modifier.weight(1f, fill = false))
        BlockCursor(Modifier.padding(start = 2.dp))
    }
}

/** A `// section` header followed by its content, for the panes that have more than one thing to say. */
@Composable
internal fun PaneSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label)
        content()
    }
}

/** 2dp, flat, 40dp tall: the accent-filled primary action, or an outlined one. Disabled drops to `ln`. */
@Composable
internal fun TerminalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    val c = Act.colors
    val fill = when {
        !enabled -> c.sf2
        primary -> c.acc
        else -> c.sf
    }
    val ink = when {
        !enabled -> c.dim
        primary -> c.onAcc
        else -> c.ink
    }
    ButtonRow(
        modifier
            .clip(ActSmall)
            .background(fill)
            .border(BorderStroke(1.dp, if (primary && enabled) c.acc else c.ln), ActSmall)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) { Text(text, style = Act.type.body, color = ink) }
}

@Composable
private fun ButtonRow(modifier: Modifier, content: @Composable () -> Unit) {
    Row(
        modifier.defaultMinSize(minHeight = 40.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) { content() }
}

/** A text-only action: accent, underlined, no box. Disabled drops to `dim`. */
@Composable
internal fun TerminalLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Text(
        text,
        style = Act.type.body.copy(textDecoration = TextDecoration.Underline),
        color = if (enabled) Act.colors.acc else Act.colors.dim,
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
    )
}

/** A boxed mono field: the label above, a `ln` border, an optional hint below. */
@Composable
internal fun TerminalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    minLines: Int = 1,
    hint: String? = null,
) {
    val c = Act.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = Act.type.tag, color = c.dim)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly,
            enabled = enabled,
            minLines = minLines,
            textStyle = Act.type.body.copy(color = if (enabled) c.ink else c.dim),
            cursorBrush = SolidColor(c.acc),
            modifier = Modifier.fillMaxWidth().clip(ActSmall).background(c.sf).border(1.dp, c.ln, ActSmall).padding(10.dp),
        )
        hint?.let { Text(it, style = Act.type.tiny, color = c.dim) }
    }
}

/** A message box: 4dp, `sf`, a border and title in [color] (error by default) and the body in mono. */
@Composable
internal fun TerminalMessage(title: String, body: String, modifier: Modifier = Modifier, color: Color = Act.colors.error) {
    val c = Act.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(ActMedium)
            .background(c.sf)
            .border(1.dp, color, ActMedium)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = Act.type.rowTitle, color = color)
        Text(body, style = Act.type.meta, color = c.ink)
    }
}

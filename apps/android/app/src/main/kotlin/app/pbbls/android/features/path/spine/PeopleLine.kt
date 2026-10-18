package app.pbbls.android.features.path.spine

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.ui.render.GlyphImage
import app.pbbls.android.features.path.spine.logic.PeopleLine

/** The glyph's box in an entry's line, relative to the line's type. */
val ENTRY_GLYPH_SIZE: TextUnit = 1.25f.em

/*
 * The card strip's gaps, as fractions of its 4u glyph: the web's `gap-x-[2.2u]`,
 * `gap-y-[.6u]`, and the `ml-[.25em]` before "+N" (relative to the 2.6u line).
 */
private const val STRIP_GAP_PER_GLYPH = 2.2f / 4f
private const val STRIP_ROW_GAP_PER_GLYPH = 0.6f / 4f
private const val STRIP_MORE_GAP_PER_GLYPH = 0.25f * 2.6f / 4f

// The placeholder of a soul without a glyph: the web's `SoulMark`, a dashed 1 px square with 3 px corners.
private val PLACEHOLDER_STROKE = 1.dp
private val PLACEHOLDER_RADIUS = 3.dp
private val PLACEHOLDER_DASH = 3.dp
private val PLACEHOLDER_GAP = 2.dp

/**
 * Who was there (rule set §Many people). Nothing when the pebble is solitary.
 *
 * As a sentence (an entry): "with [g] MARA, [g] ELIO & twelve others [5 marks] +7", one
 * text node with the glyphs inline. As a strip ([asStrip], a card): the same line as a
 * centred wrapping row of `[g] NAME` items, no commas, no "&", "with" unspoken, since
 * the card's own description says it.
 *
 * @param namesById soul id → name, from the pebble's own souls.
 * @param soulsById the glyphs; a soul missing here keeps its slot as a dashed placeholder.
 * @param style the line's base type: `type.people` for an entry, the card's `2.6u` for a card.
 * @param glyphSize [ENTRY_GLYPH_SIZE] for an entry, `4u` for a card; the strip's gaps follow it.
 * @param othersStyle the strip's "& twelve others"; the sentence sets it in the base style's italic.
 */
@Composable
fun PeopleLine(
    line: PeopleLine,
    namesById: Map<String, String>,
    soulsById: Map<String, SoulWithGlyph>,
    style: TextStyle,
    glyphSize: TextUnit,
    modifier: Modifier = Modifier,
    asStrip: Boolean = false,
    nameStyle: TextStyle = PebblesTheme.spine.type.soulName,
    moreStyle: TextStyle = PebblesTheme.spine.type.entryTime,
    glyphInk: Color = PebblesTheme.spine.colors.ink2,
    othersStyle: TextStyle = style.copy(fontStyle = FontStyle.Italic),
) {
    if (line.named.isEmpty()) return
    val others = if (line.others > 0) pluralStringResource(R.plurals.path_spine_others, line.others, line.othersWords) else ""
    if (asStrip) {
        PeopleStrip(line, namesById, soulsById, others, othersStyle, nameStyle, moreStyle, glyphSize, glyphInk, modifier)
    } else {
        PeopleSentence(line, namesById, soulsById, others, style, nameStyle, moreStyle, glyphSize, glyphInk, modifier)
    }
}

@Composable
private fun PeopleSentence(
    line: PeopleLine,
    namesById: Map<String, String>,
    soulsById: Map<String, SoulWithGlyph>,
    others: String,
    style: TextStyle,
    nameStyle: TextStyle,
    moreStyle: TextStyle,
    glyphSize: TextUnit,
    glyphInk: Color,
    modifier: Modifier,
) {
    val colors = PebblesTheme.spine.colors
    val with = stringResource(R.string.path_spine_with)
    val spans =
        remember(colors, nameStyle, moreStyle) {
            PeopleLineSpans(
                aside = TextStyle(fontStyle = FontStyle.Italic).toSpanStyle(),
                name = nameStyle.toSpanStyle().copy(color = colors.ink),
                more = moreStyle.toSpanStyle().copy(color = colors.ink2),
            )
        }
    val text =
        remember(line, namesById, soulsById, with, others, spans) {
            peopleLineText(line, namesById, PeopleLineLabels(with, others), spans)
        }
    val inlineContent =
        remember(line, soulsById, glyphSize, glyphInk) {
            (line.named + line.marks).associate { id ->
                GLYPH_INLINE_PREFIX + id to
                    InlineTextContent(Placeholder(glyphSize, glyphSize, PlaceholderVerticalAlign.TextCenter)) {
                        SoulMark(soulsById[id]?.glyph, glyphInk, Modifier.fillMaxSize())
                    }
            }
        }

    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = colors.ink2,
        inlineContent = inlineContent,
    )
}

@Composable
private fun PeopleStrip(
    line: PeopleLine,
    namesById: Map<String, String>,
    soulsById: Map<String, SoulWithGlyph>,
    others: String,
    othersStyle: TextStyle,
    nameStyle: TextStyle,
    moreStyle: TextStyle,
    glyphSize: TextUnit,
    glyphInk: Color,
    modifier: Modifier,
) {
    val colors = PebblesTheme.spine.colors
    val glyphDp = with(LocalDensity.current) { glyphSize.toDp() }
    val items =
        remember(line, namesById, soulsById, others) {
            peopleStripItems(line, namesById, { soulsById.containsKey(it) }, others)
        }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(glyphDp * STRIP_GAP_PER_GLYPH, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(glyphDp * STRIP_ROW_GAP_PER_GLYPH),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            when (item) {
                is StripItem.Named ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SoulMark(soulsById[item.id]?.glyph, glyphInk, Modifier.size(glyphDp))
                        Text(text = item.name, style = nameStyle, color = colors.ink, maxLines = 1)
                    }
                is StripItem.Others ->
                    Text(text = item.text, style = othersStyle, color = colors.ink2)
                is StripItem.Marks ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        item.ids.forEach { id -> SoulMark(soulsById[id]?.glyph, glyphInk, Modifier.size(glyphDp)) }
                        if (item.overflow > 0) {
                            Text(
                                text = "+${item.overflow}",
                                style = moreStyle,
                                color = colors.ink2,
                                modifier = Modifier.padding(start = glyphDp * STRIP_MORE_GAP_PER_GLYPH),
                            )
                        }
                    }
            }
        }
    }
}

/** A soul's glyph as a thin line, or, without one, the dashed placeholder square (web `SoulMark`). */
@Composable
private fun SoulMark(
    glyph: Glyph?,
    ink: Color,
    modifier: Modifier,
) {
    if (glyph != null) {
        GlyphImage(strokes = glyph.strokes, viewBox = glyph.viewBox, strokeColor = ink, modifier = modifier)
    } else {
        val placeholder = PebblesTheme.spine.colors.ink3
        Canvas(modifier) {
            val stroke = PLACEHOLDER_STROKE.toPx()
            val inset = stroke / 2
            drawRoundRect(
                color = placeholder,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(PLACEHOLDER_RADIUS.toPx()),
                style =
                    Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(PLACEHOLDER_DASH.toPx(), PLACEHOLDER_GAP.toPx())),
                    ),
            )
        }
    }
}

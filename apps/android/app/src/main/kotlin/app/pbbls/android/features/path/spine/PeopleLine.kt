package app.pbbls.android.features.path.spine

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.ui.render.GlyphImage
import app.pbbls.android.features.path.spine.logic.PeopleLine

/** The glyph's box in an entry's line, relative to the line's type. */
val ENTRY_GLYPH_SIZE: TextUnit = 1.25f.em

/**
 * "with [g] MARA, [g] ELIO & twelve others [5 marks] +7" (rule set §Many people), one
 * text node with the glyphs inline. Nothing when the pebble is solitary. A card keeps
 * "with" out of the visible line ([withLabel] false): its own description says it.
 *
 * @param namesById soul id → name, from the pebble's own souls.
 * @param soulsById the glyphs; a soul missing here is named without one.
 * @param style the line's base type: `type.people` for an entry, the card's `2.6u` for a card.
 * @param glyphSize [ENTRY_GLYPH_SIZE] for an entry, `4u` for a card.
 */
@Composable
fun PeopleLine(
    line: PeopleLine,
    namesById: Map<String, String>,
    soulsById: Map<String, SoulWithGlyph>,
    style: TextStyle,
    glyphSize: TextUnit,
    modifier: Modifier = Modifier,
    withLabel: Boolean = true,
    nameStyle: TextStyle = PebblesTheme.spine.type.soulName,
    moreStyle: TextStyle = PebblesTheme.spine.type.entryTime,
    glyphInk: Color = PebblesTheme.spine.colors.ink2,
) {
    if (line.named.isEmpty()) return
    val colors = PebblesTheme.spine.colors
    val labels =
        PeopleLineLabels(
            with = if (withLabel) stringResource(R.string.path_spine_with) else null,
            others = if (line.others > 0) pluralStringResource(R.plurals.path_spine_others, line.others, line.othersWords) else "",
        )
    val spans =
        remember(colors, nameStyle, moreStyle) {
            PeopleLineSpans(
                aside = TextStyle(fontStyle = FontStyle.Italic).toSpanStyle(),
                name = nameStyle.toSpanStyle().copy(color = colors.ink),
                more = moreStyle.toSpanStyle().copy(color = colors.ink2),
            )
        }
    val text =
        remember(line, namesById, soulsById, labels.with, labels.others, spans) {
            peopleLineText(line, namesById, { soulsById.containsKey(it) }, labels, spans)
        }
    val inlineContent =
        remember(line, soulsById, glyphSize, glyphInk) {
            (line.named + line.marks)
                .mapNotNull { id -> soulsById[id]?.let { id to it } }
                .associate { (id, soul) ->
                    GLYPH_INLINE_PREFIX + id to
                        InlineTextContent(Placeholder(glyphSize, glyphSize, PlaceholderVerticalAlign.TextCenter)) {
                            GlyphImage(
                                strokes = soul.glyph.strokes,
                                viewBox = soul.glyph.viewBox,
                                strokeColor = glyphInk,
                                modifier = Modifier.fillMaxSize(),
                            )
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

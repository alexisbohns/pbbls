package app.pbbls.android.features.path.spine

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.pbbls.android.features.path.spine.logic.PeopleLine

/** Inline-content id of a soul's glyph in a people line: [GLYPH_INLINE_PREFIX] + soul id. */
internal const val GLYPH_INLINE_PREFIX = "g:"

/**
 * What stands in for a glyph in the plain text: zero-width, so a screen reader
 * passes over it and a missing glyph leaves no mark.
 */
internal const val GLYPH_ALTERNATE = "​"

/** The spans a people line is set in; the base style is the `Text`'s own. */
internal class PeopleLineSpans(
    /** "with" and "& twelve others": the quiet italic. */
    val aside: SpanStyle,
    /** A named soul, in small caps. */
    val name: SpanStyle,
    /** "+7", lining tabular. */
    val more: SpanStyle,
)

/** The localised words: "with" (null when the line keeps it unspoken) and the formatted "& twelve others". */
internal class PeopleLineLabels(
    val with: String?,
    val others: String,
)

/**
 * "with [g] MARA, [g] ELIO & twelve others [g][g][g][g][g] +7", the web's
 * `PeopleLine` as one `AnnotatedString` (rule set §Many people). Glyphs are
 * inline content keyed [GLYPH_INLINE_PREFIX] + id, only for souls [hasGlyph]
 * knows; the composable supplies the drawings. Pure, so the shape is tested
 * without a Compose runtime.
 */
internal fun peopleLineText(
    line: PeopleLine,
    namesById: Map<String, String>,
    hasGlyph: (String) -> Boolean,
    labels: PeopleLineLabels,
    spans: PeopleLineSpans,
): AnnotatedString =
    buildAnnotatedString {
        labels.with?.let { with ->
            withStyle(spans.aside) { append(with) }
            append(' ')
        }
        // "A", "A & B", "A, B & C" — or "A, B" when "& N others" follows.
        val ampersandAt = if (line.others > 0) -1 else line.named.lastIndex
        line.named.forEachIndexed { index, id ->
            if (index > 0) {
                if (index == ampersandAt) withStyle(spans.aside) { append(" & ") } else append(", ")
            }
            if (hasGlyph(id)) {
                appendInlineContent(GLYPH_INLINE_PREFIX + id, GLYPH_ALTERNATE)
                append(' ')
            }
            withStyle(spans.name) { append(namesById[id].orEmpty()) }
        }
        if (line.others > 0) {
            append(' ')
            withStyle(spans.aside) { append(labels.others) }
        }
        val marks = line.marks.filter(hasGlyph)
        if (marks.isNotEmpty()) {
            append(' ')
            marks.forEach { appendInlineContent(GLYPH_INLINE_PREFIX + it, GLYPH_ALTERNATE) }
        }
        if (line.overflow > 0) {
            append(' ')
            withStyle(spans.more) { append("+${line.overflow}") }
        }
    }

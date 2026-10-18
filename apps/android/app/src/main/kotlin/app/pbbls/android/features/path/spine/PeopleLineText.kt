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
 * `PeopleLine` as one `AnnotatedString` (rule set §Many people). Every named soul
 * and every mark is inline content keyed [GLYPH_INLINE_PREFIX] + id; the composable
 * supplies the drawing, a glyph or the dashed placeholder of a soul without one,
 * as the web's `SoulMark` does. Pure, so the shape is tested without a Compose
 * runtime.
 */
internal fun peopleLineText(
    line: PeopleLine,
    namesById: Map<String, String>,
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
            appendInlineContent(GLYPH_INLINE_PREFIX + id, GLYPH_ALTERNATE)
            append(' ')
            withStyle(spans.name) { append(namesById[id].orEmpty()) }
        }
        if (line.others > 0) {
            append(' ')
            withStyle(spans.aside) { append(labels.others) }
        }
        if (line.marks.isNotEmpty()) {
            append(' ')
            line.marks.forEach { appendInlineContent(GLYPH_INLINE_PREFIX + it, GLYPH_ALTERNATE) }
        }
        if (line.overflow > 0) {
            append(' ')
            withStyle(spans.more) { append("+${line.overflow}") }
        }
    }

/** One item of a card's souls strip: the web's `.sl` flex children. */
internal sealed interface StripItem {
    /** A named soul: its mark (a glyph, or the placeholder when it has none) and its name. */
    data class Named(
        val id: String,
        val name: String,
        val glyph: Boolean,
    ) : StripItem

    /** "& twelve others", in the quiet italic. */
    data class Others(
        val text: String,
    ) : StripItem

    /** The marks of the others (placeholders for those without a glyph), then "+N" when some are left. */
    data class Marks(
        val ids: List<String>,
        val overflow: Int,
    ) : StripItem
}

/**
 * The card's strip (web `PeopleLine` card variant): the same line as items of a
 * wrapping row, no commas, no "&", "with" unspoken. A soul without a glyph keeps
 * its slot as a placeholder, named or marked, as the web's `SoulMark` does.
 */
internal fun peopleStripItems(
    line: PeopleLine,
    namesById: Map<String, String>,
    hasGlyph: (String) -> Boolean,
    others: String,
): List<StripItem> =
    buildList {
        line.named.forEach { add(StripItem.Named(it, namesById[it].orEmpty(), hasGlyph(it))) }
        if (line.others > 0) add(StripItem.Others(others))
        if (line.marks.isNotEmpty() || line.overflow > 0) add(StripItem.Marks(line.marks, line.overflow))
    }

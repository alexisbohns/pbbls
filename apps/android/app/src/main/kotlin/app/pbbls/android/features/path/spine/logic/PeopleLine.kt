package app.pbbls.android.features.path.spine.logic

import app.pbbls.android.core.common.NumberWords
import java.util.Locale

/**
 * Who a pebble was with, as one line (#1052, spec §Many people):
 *
 *   0 → no line · 1 → A · 2 → A & B · 3 → A, B & C
 *   n ≥ 4 → A, B "& {n − 2 in words} others", up to five glyph marks of the
 *           others, then +{n − 2 − 5} when positive
 *
 * e.g. at 14: "with MARA, ELIO & twelve others [5 marks] +7". Souls are ordered
 * as the persons of the week are, so the most present people are the named ones.
 */
data class PeopleLine(
    /** Soul ids set by name. Empty means the pebble is solitary: no line at all. */
    val named: List<String>,
    /** How many souls are folded into "& N others" (0 when everyone is named). */
    val others: Int,
    /** [others] in words, e.g. "twelve"; "" when 0. */
    val othersWords: String,
    /** Soul ids of the others drawn as glyph marks, at most five. */
    val marks: List<String>,
    /** Others left after the marks, shown as "+N"; 0 means no "+". */
    val overflow: Int,
)

/** Port of `people-line.ts`. */
object PeopleLines {
    const val MARKS = 5

    /** Named in full up to this many souls. */
    const val NAMED_IN_FULL = 3

    /** The held card has room to name more (pebble card spec §Recto). */
    const val NAMED_ON_CARD = 8

    /**
     * @param soulIds the pebble's souls.
     * @param order soul ids in persons-of-the-week order; ids missing from it
     *   keep their relative order after the ranked ones.
     * @param namedInFull everyone is named up to this many souls; the Spine keeps
     *   [NAMED_IN_FULL], the held card passes [NAMED_ON_CARD].
     */
    fun of(
        soulIds: List<String>,
        order: List<String>,
        locale: Locale,
        namedInFull: Int = NAMED_IN_FULL,
    ): PeopleLine {
        val rank = order.withIndex().associate { (index, id) -> id to index }
        val ranked =
            soulIds
                .distinct()
                .withIndex()
                .sortedWith(compareBy({ rank[it.value] ?: Int.MAX_VALUE }, { it.index }))
                .map { it.value }

        if (ranked.size <= namedInFull) {
            return PeopleLine(named = ranked, others = 0, othersWords = "", marks = emptyList(), overflow = 0)
        }

        val named = ranked.take(2)
        val rest = ranked.drop(2)
        val marks = rest.take(MARKS)
        return PeopleLine(
            named = named,
            others = rest.size,
            // "others" is "autres" in French, the same for either gender above one.
            othersWords = NumberWords.cardinal(rest.size, locale),
            marks = marks,
            overflow = rest.size - marks.size,
        )
    }
}

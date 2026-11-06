package app.pbbls.android.core.ui.souls

import app.pbbls.android.core.model.SoulWithGlyph
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** One letter of the A–Z list. */
data class SoulSection(
    val letter: String,
    val souls: List<SoulWithGlyph>,
)

/**
 * Ranking, grouping and folded search over the souls already fetched (souls spec S1, S3). Pure:
 * no back-end, no state. Mirrors the statics of iOS `SoulChooserModel`; the Life screen's strip
 * reads [usual] too, so both show the same people.
 */
object SoulRanking {
    /** Two rows of four. */
    const val USUAL_CAP = 8

    /** How many of [usual] the Life screen's strip shows (record-flow D10). */
    const val STRIP_CAP = 4

    private const val OTHER = "#"
    private val combiningMarks = Regex("\\p{Mn}+")

    private fun collator(): Collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.SECONDARY }

    /** Name order by the locale's collator; equal names fall back to the id so a tie never reshuffles. */
    private fun byName(names: Collator): Comparator<SoulWithGlyph> =
        Comparator { a, b -> names.compare(a.name, b.name).takeIf { it != 0 } ?: a.id.compareTo(b.id) }

    fun folded(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD).replace(combiningMarks, "").lowercase(Locale.ROOT)

    fun usual(souls: List<SoulWithGlyph>): List<SoulWithGlyph> {
        val names = collator()
        return souls
            .filter { it.pebblesCount > 0 }
            .sortedWith(compareByDescending<SoulWithGlyph> { it.pebblesCount }.then(byName(names)))
            .take(USUAL_CAP)
    }

    fun letter(name: String): String {
        val first = folded(name.trim()).firstOrNull()
        return if (first != null && first.isLetter()) first.uppercaseChar().toString() else OTHER
    }

    fun sections(souls: List<SoulWithGlyph>): List<SoulSection> {
        val names = collator()
        val grouped = souls.sortedWith(byName(names)).groupBy { letter(it.name) }
        val letters = grouped.keys.filter { it != OTHER }.sortedWith { a, b -> names.compare(a, b) }
        return (if (OTHER in grouped) letters + OTHER else letters).map { SoulSection(it, grouped.getValue(it)) }
    }

    fun results(
        souls: List<SoulWithGlyph>,
        query: String,
    ): List<SoulWithGlyph> {
        val needle = folded(query.trim())
        if (needle.isEmpty()) return emptyList()
        val names = collator()
        return souls.filter { folded(it.name).contains(needle) }.sortedWith(byName(names))
    }

    /** The Add row and the lit plus: a query nobody's folded name equals. */
    fun canAdd(
        souls: List<SoulWithGlyph>,
        query: String,
    ): Boolean {
        val needle = folded(query.trim())
        return needle.isNotEmpty() && souls.none { folded(it.name) == needle }
    }

    /** Where [query] matched in [name], in [name]'s own indices, for mock 11's highlight. */
    fun matchRange(
        name: String,
        query: String,
    ): IntRange? {
        val needle = folded(query.trim())
        if (needle.isEmpty()) return null
        val flat = StringBuilder()
        val origin = ArrayList<Int>(name.length)
        name.forEachIndexed { index, char ->
            folded(char.toString()).forEach {
                flat.append(it)
                origin.add(index)
            }
        }
        val start = flat.indexOf(needle)
        return if (start < 0) null else origin[start]..origin[start + needle.length - 1]
    }
}

package app.pbbls.android.features.path.spine

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import app.pbbls.android.features.path.spine.logic.PeopleLines
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** Foundation keeps the tag internal; the string is its public contract with `inlineContent`. */
private const val INLINE_CONTENT_TAG = "androidx.compose.foundation.text.inlineContent"

/** The four shapes of the rule set's §Many people table, as the line is set. */
class PeopleLineTextTest {
    private val spans =
        PeopleLineSpans(
            aside = SpanStyle(fontStyle = FontStyle.Italic),
            name = SpanStyle(fontWeight = FontWeight.Bold, color = Color.Black),
            more = SpanStyle(color = Color.Gray),
        )
    private val names = mapOf("mara" to "Mara", "elio" to "Elio", "bruno" to "Bruno")

    private fun line(
        vararg ids: String,
        with: Boolean = true,
        others: String = "",
    ): AnnotatedString {
        val line = PeopleLines.of(ids.toList(), ids.toList(), Locale.ENGLISH)
        return peopleLineText(
            line = line,
            namesById = names + ids.filterNot { it in names }.associateWith { "" },
            hasGlyph = { it != "bruno" },
            labels = PeopleLineLabels(with = if (with) "with" else null, others = others),
            spans = spans,
        )
    }

    /** The text with the glyph stand-ins (and the space after each) folded away. */
    private fun AnnotatedString.words(): String = text.replace("$GLYPH_ALTERNATE ", "").replace(GLYPH_ALTERNATE, "")

    private fun AnnotatedString.glyphs(): List<String> =
        getStringAnnotations(INLINE_CONTENT_TAG, 0, length).map { it.item.removePrefix(GLYPH_INLINE_PREFIX) }

    @Test
    fun `one soul`() {
        val text = line("mara")
        assertEquals("with Mara", text.words())
        assertEquals(listOf("mara"), text.glyphs())
    }

    @Test
    fun `two souls join with an ampersand`() {
        assertEquals("with Mara & Elio", line("mara", "elio").words())
    }

    @Test
    fun `three souls, a comma then an ampersand`() {
        val text = line("mara", "elio", "bruno")
        assertEquals("with Mara, Elio & Bruno", text.words())
        // Bruno has no glyph: named, but no inline mark.
        assertEquals(listOf("mara", "elio"), text.glyphs())
    }

    @Test
    fun `fourteen souls fold into others, five marks and an overflow`() {
        val ids = arrayOf("mara", "elio") + (1..12).map { "s$it" }
        val text = line(*ids, others = "& twelve others")
        assertEquals("with Mara, Elio & twelve others +7", text.words())
        assertEquals(listOf("mara", "elio", "s1", "s2", "s3", "s4", "s5"), text.glyphs())
        val italics = text.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }.map { text.text.substring(it.start, it.end) }
        assertEquals(listOf("with", "& twelve others"), italics)
        val more = text.spanStyles.single { it.item.color == Color.Gray }
        assertEquals("+7", text.text.substring(more.start, more.end))
    }

    @Test
    fun `a card keeps with unspoken`() {
        assertEquals("Mara & Elio", line("mara", "elio", with = false).words())
    }

    @Test
    fun `the card strip lists named souls, the others, then the marks with the overflow`() {
        val ids = listOf("mara", "elio", "bruno") + (1..11).map { "s$it" }
        val items = peopleStripItems(PeopleLines.of(ids, ids, Locale.ENGLISH), names, { it != "bruno" }, "& twelve others")
        assertEquals(
            listOf(
                StripItem.Named("mara", "Mara", glyph = true),
                StripItem.Named("elio", "Elio", glyph = true),
                StripItem.Others("& twelve others"),
                // Bruno is the first of the others and has no glyph: his mark is dropped, the count is not.
                StripItem.Marks(listOf("s1", "s2", "s3", "s4"), overflow = 7),
            ),
            items,
        )
        assertEquals(
            listOf(StripItem.Named("mara", "Mara", glyph = true), StripItem.Named("bruno", "Bruno", glyph = false)),
            peopleStripItems(PeopleLines.of(listOf("mara", "bruno"), emptyList(), Locale.ENGLISH), names, { it != "bruno" }, ""),
        )
    }
}

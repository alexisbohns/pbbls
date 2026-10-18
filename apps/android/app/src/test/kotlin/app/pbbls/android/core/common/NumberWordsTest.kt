package app.pbbls.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class NumberWordsTest {
    private val en = Locale.ENGLISH
    private val fr = Locale.FRENCH

    @Test
    fun `cardinals read in both languages`() {
        val table =
            listOf(
                Triple(0, "zero", "zéro"),
                Triple(1, "one", "un"),
                Triple(12, "twelve", "douze"),
                Triple(17, "seventeen", "dix-sept"),
                Triple(20, "twenty", "vingt"),
                Triple(21, "twenty-one", "vingt et un"),
                Triple(22, "twenty-two", "vingt-deux"),
                Triple(31, "thirty-one", "trente et un"),
                Triple(41, "forty-one", "quarante et un"),
                Triple(53, "fifty-three", "cinquante-trois"),
                Triple(60, "sixty", "soixante"),
            )
        for ((n, e, f) in table) {
            assertEquals(e, NumberWords.cardinal(n, en))
            assertEquals(f, NumberWords.cardinal(n, fr))
        }
    }

    @Test
    fun `cardinals agree with a feminine noun in French on the ones`() {
        val feminine = NumberWords.Gender.FEMININE
        assertEquals("une", NumberWords.cardinal(1, fr, feminine))
        assertEquals("vingt et une", NumberWords.cardinal(21, fr, feminine))
        assertEquals("cinquante et une", NumberWords.cardinal(51, fr, feminine))
        assertEquals("deux", NumberWords.cardinal(2, fr, feminine))
        assertEquals("one", NumberWords.cardinal(1, en, feminine))
    }

    @Test
    fun `cardinals fall back to digits outside 0 to 60`() {
        assertEquals("61", NumberWords.cardinal(61, en))
        assertEquals("-1", NumberWords.cardinal(-1, fr))
    }

    @Test
    fun `ordinals read in both languages`() {
        val table =
            listOf(
                Triple(1, "first", "premier"),
                Triple(2, "second", "deuxième"),
                Triple(5, "fifth", "cinquième"),
                Triple(9, "ninth", "neuvième"),
                Triple(11, "eleventh", "onzième"),
                Triple(12, "twelfth", "douzième"),
                Triple(20, "twentieth", "vingtième"),
                Triple(21, "twenty-first", "vingt et unième"),
                Triple(25, "twenty-fifth", "vingt-cinquième"),
                Triple(31, "thirty-first", "trente et unième"),
                Triple(39, "thirty-ninth", "trente-neuvième"),
                Triple(40, "fortieth", "quarantième"),
                Triple(41, "forty-first", "quarante et unième"),
                Triple(44, "forty-fourth", "quarante-quatrième"),
                Triple(53, "fifty-third", "cinquante-troisième"),
            )
        for ((n, e, f) in table) {
            assertEquals(e, NumberWords.ordinal(n, en))
            assertEquals(f, NumberWords.ordinal(n, fr))
        }
    }

    @Test
    fun `the first week is la premiere semaine`() {
        val feminine = NumberWords.Gender.FEMININE
        assertEquals("première", NumberWords.ordinal(1, fr, feminine))
        assertEquals("vingt et unième", NumberWords.ordinal(21, fr, feminine))
    }

    @Test
    fun `ordinals fall back to digits outside 1 to 53`() {
        assertEquals("0", NumberWords.ordinal(0, en))
        assertEquals("54", NumberWords.ordinal(54, fr))
    }

    @Test
    fun `english days are ordinals`() {
        assertEquals("first", NumberWords.dayOfMonth(1, en))
        assertEquals("twenty-first", NumberWords.dayOfMonth(21, en))
        assertEquals("thirty-first", NumberWords.dayOfMonth(31, en))
    }

    @Test
    fun `french days are cardinals with premier for the first`() {
        assertEquals("premier", NumberWords.dayOfMonth(1, fr))
        assertEquals("cinq", NumberWords.dayOfMonth(5, fr))
        assertEquals("vingt et un", NumberWords.dayOfMonth(21, fr))
        assertEquals("trente et un", NumberWords.dayOfMonth(31, fr))
    }

    @Test
    fun `days fall back to digits outside 1 to 31`() {
        assertEquals("32", NumberWords.dayOfMonth(32, en))
    }
}

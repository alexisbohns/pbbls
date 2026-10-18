package app.pbbls.android.features.path.spine.logic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** The web's `week-opening.test.ts`, replayed. The brief's week: Monday 5 October 2026. */
class WeekWordsTest {
    private val en = Locale.ENGLISH
    private val fr = Locale.FRENCH
    private val w41 = LocalDate.of(2026, 10, 5)

    @Test
    fun `sets the brief's week in words`() {
        assertEquals(
            WeekWords(
                number = 41,
                year = 2026,
                ordinal = "forty-first",
                title = WeekTitle.SameMonth(from = "fifth", to = "eleventh", month = "October"),
                previousNumber = 40,
                nextNumber = 42,
                nextOrdinal = "forty-second",
                nextMonday = "twelfth",
            ),
            WeekWordsBuilder.of(w41, en),
        )
    }

    @Test
    fun `reads a French week as feminine and its days as cardinals`() {
        val words = WeekWordsBuilder.of(w41, fr)
        assertEquals("quarante et unième", words.ordinal)
        assertEquals(WeekTitle.SameMonth(from = "cinq", to = "onze", month = "octobre"), words.title)
        assertEquals(42, words.nextNumber)
        assertEquals("quarante-deuxième", words.nextOrdinal)
        assertEquals("douze", words.nextMonday)
    }

    @Test
    fun `names both months when the week crosses one`() {
        // Monday 28 September to Sunday 4 October 2026.
        val words = WeekWordsBuilder.of(LocalDate.of(2026, 9, 28), fr)
        assertEquals(
            WeekTitle.CrossMonth(from = "vingt-huit", fromMonth = "septembre", to = "quatre", toMonth = "octobre"),
            words.title,
        )
        assertEquals("cinq", words.nextMonday)
    }

    @Test
    fun `takes the ISO year and wraps the week numbers at the new year`() {
        // Monday 28 December 2026 is week 53 of 2026; the next is week 1 of 2027.
        val words = WeekWordsBuilder.of(LocalDate.of(2026, 12, 28), fr)
        assertEquals(53, words.number)
        assertEquals(2026, words.year)
        assertEquals(52, words.previousNumber)
        assertEquals(1, words.nextNumber)
        assertEquals("première", words.nextOrdinal)
        assertEquals("quatre", words.nextMonday)
        // Monday 29 December 2025 already belongs to ISO 2026.
        val wrap = WeekWordsBuilder.of(LocalDate.of(2025, 12, 29), en)
        assertEquals(1, wrap.number)
        assertEquals(2026, wrap.year)
        assertEquals("first", wrap.ordinal)
    }

    @Test
    fun `says premier for the first of a month in French`() {
        // Monday 1 March 2027.
        val title = WeekWordsBuilder.of(LocalDate.of(2027, 3, 1), fr).title as WeekTitle.SameMonth
        assertEquals("premier", title.from)
        assertEquals("sept", title.to)
    }

    @Test
    fun `summary agrees in gender in French`() {
        assertEquals("un" to "une", WeekWordsBuilder.summary(1, 1, fr))
        assertEquals("vingt et un" to "trente et une", WeekWordsBuilder.summary(21, 31, fr))
        assertEquals("eleven" to "five", WeekWordsBuilder.summary(11, 5, en))
    }

    @Test
    fun `sentence case capitalises the first letter only`() {
        assertEquals("Eleven pebbles, five people.", WeekWordsBuilder.sentenceCase("eleven pebbles, five people.", en))
        assertEquals("Onze galets.", WeekWordsBuilder.sentenceCase("onze galets.", fr))
        assertEquals("", WeekWordsBuilder.sentenceCase("", en))
    }

    @Test
    fun `weekday letters run Monday first, lowercase`() {
        assertEquals(listOf("m", "t", "w", "t", "f", "s", "s"), WeekWordsBuilder.weekdayLetters(w41, en))
        assertEquals(listOf("l", "m", "m", "j", "v", "s", "d"), WeekWordsBuilder.weekdayLetters(w41, fr))
    }

    @Test
    fun `dot days name the marked days with their dates`() {
        assertEquals(
            4 to "Monday 5, Tuesday 6, Thursday 8, and Saturday 10",
            WeekWordsBuilder.dotDays(booleanArrayOf(true, true, false, true, false, true, false), w41, en),
        )
        assertEquals(
            1 to "dimanche 11",
            WeekWordsBuilder.dotDays(booleanArrayOf(false, false, false, false, false, false, true), w41, fr),
        )
    }

    @Test
    fun `dot days join two and three the way each language lists`() {
        val two = booleanArrayOf(true, false, false, false, false, false, true)
        assertEquals(2 to "Monday 5 and Sunday 11", WeekWordsBuilder.dotDays(two, w41, en))
        assertEquals(2 to "lundi 5 et dimanche 11", WeekWordsBuilder.dotDays(two, w41, fr))
        val three = booleanArrayOf(true, true, false, false, false, false, true)
        assertEquals(3 to "lundi 5, mardi 6 et dimanche 11", WeekWordsBuilder.dotDays(three, w41, fr))
        assertEquals(0 to "", WeekWordsBuilder.dotDays(BooleanArray(7), w41, en))
    }
}

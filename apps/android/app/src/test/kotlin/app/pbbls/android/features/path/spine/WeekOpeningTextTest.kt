package app.pbbls.android.features.path.spine

import app.pbbls.android.features.path.spine.logic.WeekWordsBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** The web's `summary` message, as the Android formats set it: the plural choice stands in for `<plurals>`. */
class WeekOpeningTextTest {
    private val en =
        OpeningStrings(
            noPebbles = "no pebbles",
            pebbles = { n -> if (n == 1) "%1\$s pebble" else "%1\$s pebbles" },
            people = { n -> if (n == 1) ", %1\$s person" else ", %1\$s people" },
        )
    private val fr =
        OpeningStrings(
            noPebbles = "aucun galet",
            pebbles = { n -> if (n == 1) "%1\$s galet" else "%1\$s galets" },
            people = { n -> if (n == 1) ", %1\$s personne" else ", %1\$s personnes" },
        )

    private fun summary(
        pebbles: Int,
        people: Int,
        strings: OpeningStrings,
        locale: Locale,
    ): String {
        val (pebblesWords, peopleWords) = WeekWordsBuilder.summary(pebbles, people, locale)
        return openingSummary(pebbles, people, pebblesWords, peopleWords, strings, locale)
    }

    @Test
    fun `no pebbles is the whole sentence`() {
        assertEquals("No pebbles.", summary(0, 0, en, Locale.ENGLISH))
    }

    @Test
    fun `one pebble and nobody has no people clause`() {
        assertEquals("One pebble.", summary(1, 0, en, Locale.ENGLISH))
    }

    @Test
    fun `pebbles and people in words`() {
        assertEquals("Eleven pebbles, five people.", summary(11, 5, en, Locale.ENGLISH))
    }

    @Test
    fun `French agrees in gender`() {
        assertEquals("Un galet, une personne.", summary(1, 1, fr, Locale.FRENCH))
    }

    @Test
    fun `no pebbles but people keeps the clause`() {
        assertEquals("No pebbles, two people.", summary(0, 2, en, Locale.ENGLISH))
    }
}

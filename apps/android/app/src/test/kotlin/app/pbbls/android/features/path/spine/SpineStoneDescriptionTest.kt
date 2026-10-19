package app.pbbls.android.features.path.spine

import org.junit.Assert.assertEquals
import org.junit.Test

/** The stone's description in its two shapes, with the untitled and solitary cases (spec §Accessibility). */
class SpineStoneDescriptionTest {
    private val strings =
        StoneDescriptionStrings(
            stone = "%1\$s. %2\$s, %3\$s. %4\$s, %5\$s. %6\$s Opens the pebble.",
            blank = "%1\$s. %2\$s, %3\$s. Not a pebble yet. %4\$s Opens the moment.",
            withNames = "With %1\$s.",
            untitled = "untitled",
        )

    private fun describe(
        name: String? = "Flag march with Nadia",
        names: String = "Nadia",
        blank: Boolean = false,
    ) = stoneDescription(
        name = name,
        date = "5 October 2026",
        time = "10:15",
        kind = "Large gem",
        emotion = "Pride",
        names = names,
        blank = blank,
        strings = strings,
    )

    @Test
    fun `a pebble names itself, its moment, its stone, its emotion and its people`() {
        assertEquals(
            "Flag march with Nadia. 5 October 2026, 10:15. Large gem, Pride. With Nadia. Opens the pebble.",
            describe(),
        )
    }

    @Test
    fun `the listed people are set as given`() {
        assertEquals(
            "Flag march with Nadia. 5 October 2026, 10:15. Large gem, Pride. With Mara, Elio and Bruno. Opens the pebble.",
            describe(names = "Mara, Elio and Bruno"),
        )
    }

    @Test
    fun `a solitary pebble names nobody and leaves no double space`() {
        assertEquals(
            "Flag march with Nadia. 5 October 2026, 10:15. Large gem, Pride. Opens the pebble.",
            describe(names = ""),
        )
    }

    @Test
    fun `a double space inside the name is the name's own`() {
        assertEquals(
            "Two  spaces. 5 October 2026, 10:15. Large gem, Pride. Opens the pebble.",
            describe(name = "Two  spaces", names = ""),
        )
    }

    @Test
    fun `a nameless moment is untitled`() {
        assertEquals(
            "untitled. 5 October 2026, 10:15. Large gem, Pride. With Nadia. Opens the pebble.",
            describe(name = null),
        )
        assertEquals(
            "untitled. 5 October 2026, 10:15. Large gem, Pride. With Nadia. Opens the pebble.",
            describe(name = "  "),
        )
    }

    @Test
    fun `a moment that is not a pebble yet says so instead of a kind and an emotion`() {
        assertEquals(
            "Bread from the corner bakery. 5 October 2026, 10:15. Not a pebble yet. Opens the moment.",
            describe(name = "Bread from the corner bakery", names = "", blank = true),
        )
    }

    @Test
    fun `a blank stone keeps its people`() {
        assertEquals(
            "untitled. 5 October 2026, 10:15. Not a pebble yet. With Mara. Opens the moment.",
            describe(name = null, names = "Mara", blank = true),
        )
    }
}

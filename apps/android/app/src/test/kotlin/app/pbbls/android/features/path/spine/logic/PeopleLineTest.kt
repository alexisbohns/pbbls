package app.pbbls.android.features.path.spine.logic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** The web's `people-line.test.ts`, replayed. */
class PeopleLineTest {
    private val en = Locale.ENGLISH
    private val fr = Locale.FRENCH

    private fun ids(n: Int) = (0 until n).map { "s$it" }

    @Test
    fun `draws no line for a solitary pebble`() {
        assertEquals(
            PeopleLine(named = emptyList(), others = 0, othersWords = "", marks = emptyList(), overflow = 0),
            PeopleLines.of(emptyList(), emptyList(), en),
        )
    }

    @Test
    fun `names all souls up to three`() {
        for (n in 1..3) {
            val line = PeopleLines.of(ids(n), ids(n), en)
            assertEquals(ids(n), line.named)
            assertEquals(0, line.others)
            assertEquals(emptyList<String>(), line.marks)
            assertEquals(0, line.overflow)
        }
    }

    @Test
    fun `names two and folds the rest from four`() {
        assertEquals(
            PeopleLine(named = listOf("s0", "s1"), others = 2, othersWords = "two", marks = listOf("s2", "s3"), overflow = 0),
            PeopleLines.of(ids(4), ids(4), en),
        )
    }

    @Test
    fun `draws at most five marks at seven`() {
        val line = PeopleLines.of(ids(7), ids(7), en)
        assertEquals(2, line.named.size)
        assertEquals(5, line.others)
        assertEquals("five", line.othersWords)
        assertEquals(listOf("s2", "s3", "s4", "s5", "s6"), line.marks)
        assertEquals(0, line.overflow)
    }

    @Test
    fun `reads with A, B and twelve others, five marks, plus seven at fourteen`() {
        val line = PeopleLines.of(ids(14), ids(14), en)
        assertEquals(listOf("s0", "s1"), line.named)
        assertEquals(12, line.others)
        assertEquals("twelve", line.othersWords)
        assertEquals(5, line.marks.size)
        assertEquals(7, line.overflow)
        assertEquals("douze", PeopleLines.of(ids(14), ids(14), fr).othersWords)
    }

    @Test
    fun `follows the persons-of-the-week order, not the pebble's`() {
        val line = PeopleLines.of(listOf("elio", "mara", "nadia", "ines"), listOf("nadia", "ines", "mara", "elio"), en)
        assertEquals(listOf("nadia", "ines"), line.named)
        assertEquals(listOf("mara", "elio"), line.marks)
    }

    @Test
    fun `keeps unranked souls after the ranked ones, in their own order`() {
        val line = PeopleLines.of(listOf("x", "mara", "y"), listOf("mara"), en)
        assertEquals(listOf("mara", "x", "y"), line.named)
    }

    @Test
    fun `counts a duplicated soul once`() {
        assertEquals(listOf("a", "b"), PeopleLines.of(listOf("a", "a", "b"), listOf("a", "b"), en).named)
    }

    @Test
    fun `the held card names everyone up to eight`() {
        val line = PeopleLines.of(ids(8), ids(8), en, namedInFull = PeopleLines.NAMED_ON_CARD)
        assertEquals(ids(8), line.named)
        assertEquals(0, line.others)
    }

    @Test
    fun `past eight the card falls back to the counting rule`() {
        val line = PeopleLines.of(ids(9), ids(9), en, namedInFull = PeopleLines.NAMED_ON_CARD)
        assertEquals(ids(2), line.named)
        assertEquals(7, line.others)
        assertEquals(5, line.marks.size)
        assertEquals(2, line.overflow)
    }
}

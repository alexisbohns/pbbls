package app.pbbls.android.features.path.spine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The reading line's rule (spec §Reading day): the last day item at or above the line
 * is the day being read. Items arrive in list order, as `LazyListState` lists them.
 */
class ReadingDayTest {
    private val line = 350

    @Test
    fun `nothing visible reads no day`() {
        assertNull(readingDay(emptyList(), line))
    }

    @Test
    fun `before the first day reaches the line, no day is read`() {
        assertNull(readingDay(listOf(0 to 400, 1 to 700), line))
    }

    @Test
    fun `between two seams, the day above the line is read`() {
        assertEquals(1, readingDay(listOf(0 to -200, 1 to 100, 2 to 600), line))
    }

    @Test
    fun `past the last seam, the last day is read`() {
        assertEquals(2, readingDay(listOf(1 to -500, 2 to -40), line))
    }

    @Test
    fun `a seam exactly on the line is read`() {
        assertEquals(3, readingDay(listOf(2 to 0, 3 to 350, 4 to 351), line))
    }

    @Test
    fun `a day whose seam scrolled off is read through its pebbles`() {
        // The seam of day 1 is gone; its second pebble sits under the line, day 2's seam below it.
        assertEquals(1, readingDay(listOf(1 to -30, 1 to 200, 2 to 500), line))
    }
}

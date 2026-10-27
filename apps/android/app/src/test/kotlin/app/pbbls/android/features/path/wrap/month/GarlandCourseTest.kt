package app.pbbls.android.features.path.wrap.month

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `IntroMotionTests.course` (#1241): the garland's course runs on to its end as the leaf ties it. */
class GarlandCourseTest {
    @Test
    fun `the course runs on to its end as the leaf ties it, without a jump, whatever the month's length`() {
        val whole = Garland.courseSweep(course = 1f, leaf = 1f, daysInMonth = 31)
        listOf(28, 29, 30, 31).forEach { days ->
            val drawn = Garland.courseSweep(1f, 0f, days)
            assertEquals(Garland.courseSweep(0.999_999f, 0f, days), drawn, 1e-2f)
            val tying = Garland.courseSweep(1f, 0.5f, days)
            assertTrue(drawn <= tying && tying <= whole)
            assertEquals(whole, Garland.courseSweep(1f, 1f, days), 0f)
        }
        // Nor a jump as the leaf finishes tying: the last frame before it is the whole course.
        assertEquals(Garland.courseSweep(1f, 1f, 30), Garland.courseSweep(1f, 0.999_999f, 30), 1e-2f)
        assertTrue(Garland.courseSweep(1f, 0f, 30) < whole)
    }

    @Test
    fun `the course starts half a slot past the 1st and grows as the days land`() {
        // From its start, 0.55 of a slot past the tie, to half a slot past the 1st (slot 1), at 360 / 32 degrees a slot.
        val first = Garland.courseSweep(0f, 0f, 31)
        assertEquals((1.5f - 0.55f) * 360f / 32, first, 1e-4f)
        val half = Garland.courseSweep(0.5f, 0f, 31)
        assertTrue(first < half && half < Garland.courseSweep(1f, 0f, 31))
    }
}

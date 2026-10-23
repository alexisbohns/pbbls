package app.pbbls.android.core.ui

import app.pbbls.android.core.model.wrap.DayWeight
import app.pbbls.android.core.ui.marks.CairnGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CairnGeometryTest {
    @Test
    fun `one stone per day with a moment, widest at the base`() {
        val days = listOf(DayWeight(1, 1), DayWeight(0, 0), DayWeight(5, 3), DayWeight(2, 0))
        // 9 + 4.2·min(c, 3) + 2.4·(m − 1); a day without a pebble counts as intensity 1.
        val widths = CairnGeometry.widths(days)
        assertEquals(3, widths.size)
        listOf(26.4f, 17.4f, 13.2f).zip(widths).forEach { (expected, actual) -> assertEquals(expected, actual, 1e-4f) }
    }

    @Test
    fun `a full week stands no taller than 42 dp`() {
        val week = List(7) { DayWeight(3, 3) }
        val aspect = 1.3f
        val widths = CairnGeometry.widths(week)
        val squash = CairnGeometry.squash(widths, aspect, live = true)
        assertTrue(squash < 1f)
        assertEquals(CairnGeometry.MAX_HEIGHT, CairnGeometry.stackHeight(widths + CairnGeometry.NEXT_WIDTH, aspect) * squash, 1e-3f)
        assertEquals(1f, CairnGeometry.squash(listOf(13.2f), aspect, live = false), 0f)
    }
}

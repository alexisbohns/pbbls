package app.pbbls.android.core.ui

import androidx.compose.ui.unit.dp
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.ui.render.stone.SealSize
import org.junit.Assert.assertEquals
import org.junit.Test

class DaySetGeometryTest {
    @Test
    fun `a week row is the persons' 79 dp`() {
        assertEquals(DaySetGeometry.Box(79.dp, 7.dp), DaySetGeometry.size(7, DayMode.Row))
    }

    @Test
    fun `a calendar is as tall as its weeks`() {
        // October 2026 starts on a Thursday: three leading days, 31 days, five weeks.
        assertEquals(5, DaySetGeometry.rows(31, 3))
        // February 2027 starts on a Monday: four weeks exactly.
        assertEquals(4, DaySetGeometry.rows(28, 0))
        assertEquals(6, DaySetGeometry.rows(31, 6))
        assertEquals(DaySetGeometry.Box(79.dp, 55.dp), DaySetGeometry.size(31, DayMode.Calendar(3)))
    }

    @Test
    fun `the contents seal keeps its 20, 24 and 27`() {
        assertEquals(listOf(20.dp, 24.dp, 27.dp), ValenceSizeGroup.entries.map { SealSize.CONTENTS.width(it) })
    }
}

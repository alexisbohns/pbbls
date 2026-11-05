package app.pbbls.android.core.designsystem.stone

import org.junit.Assert.assertEquals
import org.junit.Test

/** The grid's seeds are stable and distinct per row, so the stone is the same on every launch. */
class StoneGridTest {
    @Test
    fun rowSeedsAreStableAndDistinct() {
        val seeds = StoneGrid.rowSeeds("onboarding-glyphs", rows = 3)
        assertEquals(listOf("onboarding-glyphs#0", "onboarding-glyphs#1", "onboarding-glyphs#2"), seeds)
    }

    @Test
    fun cellsAreNumberedRowMajor() {
        assertEquals(7, StoneGrid.cellIndex(row = 2, column = 1, columns = 3))
    }
}

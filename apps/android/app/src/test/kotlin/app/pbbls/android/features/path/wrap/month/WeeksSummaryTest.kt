package app.pbbls.android.features.path.wrap.month

import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.model.wrap.DayWeight
import app.pbbls.android.core.model.wrap.MonthWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `MonthCardsTests.everyWeek` / `allEmpty` (#1241 D12) on the debug fixture's October. */
class WeeksSummaryTest {
    private val october = SpineFixtureWeek.monthWrap

    @Test
    fun `every week of the month is listed, an empty one too`() {
        val weeks = WeeksSummary.weeks(october)
        assertEquals(october.weeks!!.size, weeks.size)
        assertEquals(0, weeks.first().pebbles)
        assertTrue(WeeksSummary.isShown(october))
    }

    @Test
    fun `a month whose weeks are all empty shows no weeks`() {
        val quiet = october.copy(weeks = october.weeks!!.map(::emptied))
        assertFalse(WeeksSummary.isShown(quiet))
    }

    private fun emptied(week: MonthWeek) = week.copy(pebbles = 0, soulIds = emptyList(), days = week.days.map { DayWeight(0, 0) })
}

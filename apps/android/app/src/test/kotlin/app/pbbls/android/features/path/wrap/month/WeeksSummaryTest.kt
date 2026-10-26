package app.pbbls.android.features.path.wrap.month

import app.pbbls.android.core.model.wrap.DayWeight
import app.pbbls.android.core.model.wrap.MonthWeek
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.model.wrap.MonthWrapDto
import app.pbbls.android.core.model.wrap.WrapJson
import app.pbbls.android.core.model.wrap.toModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `MonthCardsTests.everyWeek` / `allEmpty` (#1241 D12) on the server's own October payload. */
class WeeksSummaryTest {
    private val october: MonthWrap =
        WrapJson.decodeFromString<MonthWrapDto>(requireNotNull(javaClass.getResource("/month-full.json")).readText()).toModel()

    @Test
    fun `every week of the month is listed, an empty one too`() {
        val weeks = october.weeks!!
        val wrap = october.copy(weeks = weeks.mapIndexed { i, w -> if (i == 1) emptied(w) else w })
        val listed = WeeksSummary.weeks(wrap)
        assertEquals(5, listed.size)
        assertEquals(0, listed[1].pebbles)
        assertTrue(WeeksSummary.isShown(wrap))
    }

    @Test
    fun `a month whose weeks are all empty shows no weeks`() {
        val quiet = october.copy(weeks = october.weeks!!.map(::emptied))
        assertFalse(WeeksSummary.isShown(quiet))
    }

    @Test
    fun `a month without weeks lists none`() {
        val none = october.copy(weeks = null)
        assertTrue(WeeksSummary.weeks(none).isEmpty())
        assertFalse(WeeksSummary.isShown(none))
    }

    private fun emptied(week: MonthWeek) = week.copy(pebbles = 0, soulIds = emptyList(), days = week.days.map { DayWeight(0, 0) })
}

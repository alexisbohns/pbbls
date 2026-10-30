package app.pbbls.android.features.achievements

import app.pbbls.android.AchievementsFixture
import app.pbbls.android.core.model.achievements.Coverage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** The board's page, as the previews draw it: the numbers the boards were judged on (#1307). */
class AchievementsFixtureTest {
    private val page = AchievementsFixture.page

    @Test
    fun `the fixture carries the board's numbers`() {
        assertEquals(249, page.daysKept)
        assertEquals(38, page.emotions!!.seats.size)
        assertEquals(23, page.emotions!!.seats.count { it.met })
        assertEquals(2, page.emotions!!.seats.count { it.isNew })
        assertEquals(38, page.emotions!!.families.sumOf { it.met.size + it.notYet.size })
        assertEquals(14, page.domains!!.entered.size)
        assertEquals(Coverage.MOST, page.domains!!.coverage)
        assertEquals(137, page.pebbles!!.count)
        assertEquals(
            listOf(8),
            page.years
                .single()
                .months
                .filter { it.level == 2 }
                .map { it.month },
        )
        assertNotNull(page.explorer!!.firstPebble)
    }

    @Test
    fun `every seat's family and every domain resolve through the lookups`() {
        val lookups = AchievementsFixture.lookups
        page.emotions!!.seats.forEach { assertNotNull(it.family?.let(lookups.family)) }
        page.domains!!.entered.forEach { assertNotNull(it.domainId?.let(lookups.domain)) }
        page.domains!!.notYet.forEach { assertNotNull(lookups.domain(it)) }
    }
}

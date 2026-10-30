package app.pbbls.android.features.achievements

import app.pbbls.android.AchievementsFixture
import app.pbbls.android.core.model.achievements.Coverage
import app.pbbls.android.core.model.achievements.decodeAchievementsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * The previews' fixture is the canonical design fixture (#1307): the board's page in the served
 * shape, `oracles/achievements/design/design-fixture.json`, shared with iOS and on the test
 * classpath. It must map, through the app's own decoder, to exactly [AchievementsFixture.page].
 */
class AchievementsFixtureTest {
    private val page = AchievementsFixture.page

    private fun read(name: String): String =
        requireNotNull(AchievementsFixtureTest::class.java.getResource("/$name.json")) { "missing $name.json" }.readText()

    @Test
    fun `the shared design fixture maps to the Kotlin fixture, value for value`() {
        assertEquals(decodeAchievementsPage(read("design-fixture")), page)
    }

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
        page.emotions!!.seats.forEach { assertNotNull(it.emotionId?.let(lookups.emotion)) }
        page.domains!!.entered.forEach { assertNotNull(it.domainId?.let(lookups.domain)) }
        page.domains!!.notYet.forEach { assertNotNull(lookups.domain(it)) }
    }
}

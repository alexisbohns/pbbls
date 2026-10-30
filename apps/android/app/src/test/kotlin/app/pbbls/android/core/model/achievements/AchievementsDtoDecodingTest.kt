package app.pbbls.android.core.model.achievements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The dumped payloads decode as sent, before any mapping (#1307): every card, every key served. */
class AchievementsDtoDecodingTest {
    @Test
    fun `every dump decodes every card`() {
        for (name in listOf(AchievementPayloads.PAGE, AchievementPayloads.YEAR, AchievementPayloads.SEEN, AchievementPayloads.PULLED)) {
            val dto = AchievementPayloads.dto(name)
            assertNotNull(name, dto.since)
            assertNotNull(name, dto.daysKept)
            assertNotNull(name, dto.explorer)
            assertNotNull(name, dto.emotions)
            assertNotNull(name, dto.domains)
            assertNotNull(name, dto.pebbles)
            assertNotNull(name, dto.glyphsCarved)
            assertNotNull(name, dto.glyphSales)
            assertTrue(name, dto.years.isNotEmpty())
            assertEquals(name, 38, dto.emotions!!.seats.size)
            assertEquals(name, 5, dto.explorer!!.firstFive.size)
            assertEquals(name, 6, dto.explorer!!.sixFirsts.size)
            assertTrue(name, dto.shelf.isNotEmpty())
        }
    }

    @Test
    fun `a not-yet completion and a not-done first decode with their keys omitted`() {
        val explorer = AchievementPayloads.dto(AchievementPayloads.PAGE).explorer!!
        assertNull(explorer.completion!!.pulledAt)
        assertNotNull(explorer.completion!!.pebble)
        val collection = explorer.firstFive.first { it.slug == "first-collection" }
        assertNull(collection.on)
        assertNull(collection.subject)
    }

    @Test
    fun `an unnamed moment's snapshot decodes without a name`() {
        val medium =
            AchievementPayloads
                .dto(AchievementPayloads.PAGE)
                .explorer!!
                .sixFirsts
                .first { it.kind == "medium" }
        assertNotNull(medium.pebble!!.momentId)
        assertNull(medium.pebble!!.name)
    }

    @Test
    fun `the shelf's threshold is an explicit null for a family that counts nothing`() {
        val shelf = AchievementPayloads.dto(AchievementPayloads.PAGE).shelf
        assertNull(shelf.first { it.slug == "first-moment" }.threshold)
        assertEquals(1, shelf.first { it.slug == "pebble-count-1" }.threshold)
    }
}

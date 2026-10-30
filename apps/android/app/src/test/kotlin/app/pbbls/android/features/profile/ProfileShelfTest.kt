package app.pbbls.android.features.profile

import app.pbbls.android.AchievementsFixture
import app.pbbls.android.core.model.achievements.ShelfItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Profile shelf from the served row (#1307, D18): iOS's `ProfileShelfTests`, case for case. */
class ProfileShelfTest {
    @Test
    fun `no shelf served, or none earned - no badges and no count`() {
        assertTrue(ProfileShelf.badges(null, emptyMap()).isEmpty())
        assertNull(ProfileShelf.count(null))
        val none = AchievementsFixture.page.copy(shelf = emptyList(), shelfCount = 0)
        assertTrue(ProfileShelf.badges(none, emptyMap()).isEmpty())
        assertNull(ProfileShelf.count(none))
    }

    @Test
    fun `a served row without a slug still shows, from its family`() {
        val page =
            AchievementsFixture.page.copy(
                shelf = listOf(ShelfItem(slug = null, family = "pebble_count", threshold = 50, unlockedOn = null)),
            )
        val badge = ProfileShelf.badges(page, emptyMap()).single()
        assertEquals("pebble_count", badge.family)
        assertEquals(50, badge.threshold)
    }
}

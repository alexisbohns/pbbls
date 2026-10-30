package app.pbbls.android.core.ui

import app.pbbls.android.core.designsystem.PbblsIcon
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The family marks are iOS's `Achievement.familySymbol` table, family by family
 * (Android icon migration spec D4): the cross-surface mirror written as a test.
 */
class AchievementFamilyIconTest {
    @Test
    fun `each family draws what iOS draws for it`() {
        val ios =
            mapOf(
                "pebble_count" to PbblsIcon.STACK,
                "emotion_first" to PbblsIcon.HEART,
                "first_collection" to PbblsIcon.COLLECTION,
                "first_glyph" to PbblsIcon.PEBBLE_DOTTED_GLYPH,
                "first_soul" to PbblsIcon.PEOPLE,
                "first_moment" to PbblsIcon.PEBBLE_FLAT,
                "first_pebble" to PbblsIcon.PEBBLE_ROUND,
                "first_five" to PbblsIcon.STACK_LIT_3,
                "glyph_count" to PbblsIcon.PEBBLE_DOTTED_GLYPH,
            )
        ios.forEach { (family, icon) -> assertEquals(family, icon, achievementFamilyIcon(family)) }
    }

    @Test
    fun `families with no drawing of their own get the glowing pebble`() {
        listOf("domain_first", "glyph_sales", "a_family_from_the_future").forEach { family ->
            assertEquals(family, PbblsIcon.PEBBLE_GLOW_MD, achievementFamilyIcon(family))
        }
    }
}

package app.pbbls.android.core.ui

import app.pbbls.android.core.designsystem.PbblsGlyph
import app.pbbls.android.core.designsystem.glyph.GlyphFill
import app.pbbls.android.core.designsystem.glyph.GlyphForm
import app.pbbls.android.core.designsystem.glyph.GlyphTestData
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The family marks are iOS's `Achievement.familySymbol` table, family by family
 * (Android icon migration spec D4): the cross-surface mirror written as a test.
 */
class AchievementFamilyIconTest {
    @Before
    fun installCatalog() = GlyphTestData.install()

    @Test
    fun `each family draws what iOS draws for it`() {
        val ios =
            mapOf(
                "pebble_count" to PbblsGlyph.STACK,
                "emotion_first" to PbblsGlyph.HEART,
                "first_collection" to PbblsGlyph.COLLECTION,
                "first_glyph" to PbblsGlyph.PEBBLE_GLYPH,
                "first_soul" to PbblsGlyph.PEOPLE,
                "first_moment" to PbblsGlyph.PEBBLE_FLAT,
                "first_pebble" to PbblsGlyph.PEBBLE_ROUND,
                "first_five" to PbblsGlyph.STACK,
                "glyph_count" to PbblsGlyph.PEBBLE_GLYPH,
            )
        ios.forEach { (family, icon) -> assertEquals(family, icon, achievementFamilyIcon(family)) }
    }

    @Test
    fun `families with no drawing of their own get the glowing pebble`() {
        listOf("domain_first", "glyph_sales", "a_family_from_the_future").forEach { family ->
            assertEquals(family, PbblsGlyph.PEBBLE_GLOW_MD, achievementFamilyIcon(family))
        }
    }

    @Test
    fun `each family draws in the form iOS draws it in`() {
        assertEquals(GlyphForm(GlyphFill.Progress(3)), achievementFamilyForm("first_five"))
        assertEquals(GlyphForm.DOTTED, achievementFamilyForm("first_glyph"))
        assertEquals(GlyphForm.DOTTED, achievementFamilyForm("glyph_count"))
        assertEquals(GlyphForm.REGULAR, achievementFamilyForm("first_moment"))
        assertEquals(GlyphForm.REGULAR, achievementFamilyForm("a_family_from_the_future"))
    }
}

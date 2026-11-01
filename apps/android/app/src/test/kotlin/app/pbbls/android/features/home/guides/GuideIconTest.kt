package app.pbbls.android.features.home.guides

import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.model.home.GuideIllustration
import org.junit.Assert.assertEquals
import org.junit.Test

/** The About rows' chip icons (#1383 D13): one per illustration, and the pebble for none. */
class GuideIconTest {
    @Test
    fun `each illustration has its chip icon`() {
        val expected =
            mapOf(
                GuideIllustration.PEBBLES to PbblsIcon.PEBBLE_ROUND,
                GuideIllustration.STONES to PbblsIcon.STONES_ALIGNED_BOTTOM,
                GuideIllustration.EMOTIONS to PbblsIcon.STONES_TRACK,
                GuideIllustration.VALENCE to PbblsIcon.SCALE,
                GuideIllustration.DOMAINS to PbblsIcon.ABSTRACT_LANDSCAPE,
                GuideIllustration.COLLECTIONS to PbblsIcon.COLLECTION,
                GuideIllustration.SOULS to PbblsIcon.PEOPLE,
                GuideIllustration.GLYPHS to PbblsIcon.PEBBLE_DOTTED_GLYPH,
                GuideIllustration.KARMA to PbblsIcon.KARMA_DOT,
            )

        assertEquals(GuideIllustration.entries.toSet(), expected.keys)
        expected.forEach { (illustration, icon) -> assertEquals("$illustration", icon, illustration.chipIcon()) }
    }

    @Test
    fun `a guide without an illustration wears the round pebble`() {
        val none: GuideIllustration? = null

        assertEquals(PbblsIcon.PEBBLE_ROUND, none.chipIcon())
    }
}

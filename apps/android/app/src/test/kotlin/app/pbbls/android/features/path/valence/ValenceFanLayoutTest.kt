package app.pbbls.android.features.path.valence

import app.pbbls.android.features.path.models.Valence
import app.pbbls.android.features.path.models.ValencePolarity
import app.pbbls.android.features.path.models.ValenceSizeGroup
import app.pbbls.android.features.path.models.valenceAt
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fan's constants are eye-tuned; these are the two invariants that are not
 * left to the eye — ports iOS `ValenceFanLayoutTests`.
 *
 * If a future tuning pass breaks the bounds invariant, the angles come in
 * before the radii shrink: the vertical rhythm is what carries the fan.
 */
class ValenceFanLayoutTest {
    /** The margin every stone keeps from the canvas edge. */
    private val margin = 8f

    @Test
    fun `every stone stays inside the canvas with margin`() {
        Valence.entries.forEach { valence ->
            val size = valence.sizeGroup
            val left = ValenceFanLayout.left(valence)
            val top = ValenceFanLayout.top(valence)
            val right = left + ValenceFanLayout.stoneWidth(size)
            val bottom = top + ValenceFanLayout.stoneHeight(size)
            assertTrue("$valence overflows the left edge ($left)", left >= margin)
            assertTrue("$valence overflows the top edge ($top)", top >= margin)
            assertTrue(
                "$valence overflows the right edge ($right)",
                right <= ValenceFanLayout.REFERENCE_WIDTH - margin,
            )
            assertTrue(
                "$valence overflows the bottom edge ($bottom)",
                bottom <= ValenceFanLayout.REFERENCE_HEIGHT - margin,
            )
        }
    }

    @Test
    fun `polarity orders the ring left to right`() {
        ValenceSizeGroup.entries.forEach { size ->
            val lowlight = ValenceFanLayout.centreX(valenceAt(ValencePolarity.LOWLIGHT, size))
            val neutral = ValenceFanLayout.centreX(valenceAt(ValencePolarity.NEUTRAL, size))
            val highlight = ValenceFanLayout.centreX(valenceAt(ValencePolarity.HIGHLIGHT, size))
            assertTrue("$size: lowlight must sit left of neutral", lowlight < neutral)
            assertTrue("$size: highlight must sit right of neutral", neutral < highlight)
        }
    }

    @Test
    fun `stones grow with the size group`() {
        val small = ValenceFanLayout.stoneHeight(ValenceSizeGroup.SMALL)
        val medium = ValenceFanLayout.stoneHeight(ValenceSizeGroup.MEDIUM)
        val large = ValenceFanLayout.stoneHeight(ValenceSizeGroup.LARGE)
        assertTrue(small < medium)
        assertTrue(medium < large)
    }

    @Test
    fun `rings rise as the size group grows`() {
        // Compared on the side stones: the neutral one is lifted into an arc,
        // which would flatter the comparison rather than test it.
        val small = ValenceFanLayout.centreY(Valence.LOWLIGHT_SMALL)
        val medium = ValenceFanLayout.centreY(Valence.LOWLIGHT_MEDIUM)
        val large = ValenceFanLayout.centreY(Valence.LOWLIGHT_LARGE)
        assertTrue("medium must sit above small", medium < small)
        assertTrue("large must sit above medium", large < medium)
    }

    @Test
    fun `the neutral stone of every ring is lifted above its siblings`() {
        ValenceSizeGroup.entries.forEach { size ->
            val neutral = ValenceFanLayout.centreY(valenceAt(ValencePolarity.NEUTRAL, size))
            val side = ValenceFanLayout.centreY(valenceAt(ValencePolarity.LOWLIGHT, size))
            assertTrue("$size: the arc needs the centre stone lifted", neutral < side)
        }
    }
}

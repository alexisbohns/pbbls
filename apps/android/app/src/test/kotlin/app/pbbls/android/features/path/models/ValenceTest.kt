package app.pbbls.android.features.path.models

import org.junit.Assert.assertEquals
import org.junit.Test

class ValenceTest {
    @Test
    fun `maps all nine positiveness-intensity pairs`() {
        assertEquals(Valence.LOWLIGHT_SMALL, Valence.fromOrDefault(-1, 1))
        assertEquals(Valence.LOWLIGHT_MEDIUM, Valence.fromOrDefault(-1, 2))
        assertEquals(Valence.LOWLIGHT_LARGE, Valence.fromOrDefault(-1, 3))
        assertEquals(Valence.NEUTRAL_SMALL, Valence.fromOrDefault(0, 1))
        assertEquals(Valence.NEUTRAL_MEDIUM, Valence.fromOrDefault(0, 2))
        assertEquals(Valence.NEUTRAL_LARGE, Valence.fromOrDefault(0, 3))
        assertEquals(Valence.HIGHLIGHT_SMALL, Valence.fromOrDefault(1, 1))
        assertEquals(Valence.HIGHLIGHT_MEDIUM, Valence.fromOrDefault(1, 2))
        assertEquals(Valence.HIGHLIGHT_LARGE, Valence.fromOrDefault(1, 3))
    }

    @Test
    fun `out-of-range pairs fall back to neutral medium`() {
        // Unreachable through the DB CHECK constraints — decode-drift guard.
        assertEquals(Valence.NEUTRAL_MEDIUM, Valence.fromOrDefault(0, 4))
        assertEquals(Valence.NEUTRAL_MEDIUM, Valence.fromOrDefault(2, 1))
        assertEquals(Valence.NEUTRAL_MEDIUM, Valence.fromOrDefault(-2, 0))
    }

    @Test
    fun `size group follows intensity`() {
        assertEquals(ValenceSizeGroup.SMALL, Valence.HIGHLIGHT_SMALL.sizeGroup)
        assertEquals(ValenceSizeGroup.MEDIUM, Valence.LOWLIGHT_MEDIUM.sizeGroup)
        assertEquals(ValenceSizeGroup.LARGE, Valence.NEUTRAL_LARGE.sizeGroup)
    }

    @Test
    fun `polarity follows positiveness`() {
        assertEquals(ValencePolarity.LOWLIGHT, Valence.LOWLIGHT_LARGE.polarity)
        assertEquals(ValencePolarity.NEUTRAL, Valence.NEUTRAL_SMALL.polarity)
        assertEquals(ValencePolarity.HIGHLIGHT, Valence.HIGHLIGHT_MEDIUM.polarity)
    }

    @Test
    fun `asset keys are the lowercase outline segments`() {
        assertEquals("small", ValenceSizeGroup.SMALL.key)
        assertEquals("lowlight", ValencePolarity.LOWLIGHT.key)
    }
}

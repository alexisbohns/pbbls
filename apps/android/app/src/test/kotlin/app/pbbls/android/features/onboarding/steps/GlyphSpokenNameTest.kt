package app.pbbls.android.features.onboarding.steps

import app.pbbls.android.core.model.Glyph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlyphSpokenNameTest {
    private fun glyph(
        name: String?,
        isSystem: Boolean,
    ) = Glyph(id = "g", name = name, strokes = emptyList(), viewBox = "0 0 200 200", isSystem = isSystem)

    @Test
    fun `a system glyph's internal name is never spoken`() {
        assertNull(glyphSpokenName(glyph("domain:family", isSystem = true)))
    }

    @Test
    fun `a carved glyph speaks the name its owner gave it`() {
        assertEquals("Spiral", glyphSpokenName(glyph("Spiral", isSystem = false)))
    }

    @Test
    fun `no name speaks nothing`() {
        assertNull(glyphSpokenName(glyph(null, isSystem = false)))
    }
}

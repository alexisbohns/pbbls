package app.pbbls.android.features.profile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [soulFormCanSave] — the merged `SoulDraft.isValid` + `EditSoulSheet.canSave`
 * gate: create needs a non-blank trimmed name; edit additionally needs a real
 * change (trimmed name or glyph).
 */
class SoulFormLogicTest {
    private val glyphA = "4759c37c-68a6-46a6-b4fc-046bd0316752"
    private val glyphB = "0e5a9e1c-1111-2222-3333-444455556666"

    // ---- create mode (originalName == null) ----

    @Test
    fun `create enables with a non-blank name`() {
        assertTrue(soulFormCanSave(originalName = null, originalGlyphId = null, name = "Molly", glyphId = glyphA))
    }

    @Test
    fun `create disables on empty name`() {
        assertFalse(soulFormCanSave(originalName = null, originalGlyphId = null, name = "", glyphId = glyphA))
    }

    @Test
    fun `create disables on whitespace-only name`() {
        assertFalse(soulFormCanSave(originalName = null, originalGlyphId = null, name = "   ", glyphId = glyphA))
    }

    // ---- edit mode ----

    @Test
    fun `edit disables when nothing changed`() {
        assertFalse(soulFormCanSave(originalName = "Molly", originalGlyphId = glyphA, name = "Molly", glyphId = glyphA))
    }

    @Test
    fun `edit treats a whitespace-padded same name as unchanged`() {
        assertFalse(
            soulFormCanSave(originalName = "Molly", originalGlyphId = glyphA, name = "  Molly  ", glyphId = glyphA),
        )
    }

    @Test
    fun `edit enables on a name change`() {
        assertTrue(soulFormCanSave(originalName = "Molly", originalGlyphId = glyphA, name = "Maude", glyphId = glyphA))
    }

    @Test
    fun `edit enables on a glyph change alone`() {
        assertTrue(soulFormCanSave(originalName = "Molly", originalGlyphId = glyphA, name = "Molly", glyphId = glyphB))
    }

    @Test
    fun `edit never enables with a blank name, even with a glyph change`() {
        assertFalse(soulFormCanSave(originalName = "Molly", originalGlyphId = glyphA, name = " ", glyphId = glyphB))
    }
}

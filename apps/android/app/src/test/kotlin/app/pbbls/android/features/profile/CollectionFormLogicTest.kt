package app.pbbls.android.features.profile

import app.pbbls.android.core.model.CollectionMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [collectionFormCanSave] — the `EditCollectionSheet.canSave` port with create
 * folded in: create needs a non-blank trimmed name; edit additionally needs a
 * real change (trimmed name, mode or glyph id — including clearing either to
 * null).
 */
class CollectionFormLogicTest {
    // ---- create mode (originalName == null) ----

    @Test
    fun `create enables with a non-blank name, mode optional`() {
        assertTrue(
            collectionFormCanSave(
                originalName = null,
                originalMode = null,
                name = "Trips",
                mode = null,
                originalGlyphId = null,
                glyphId = null,
            ),
        )
    }

    @Test
    fun `create disables on blank name even with a mode picked`() {
        assertFalse(
            collectionFormCanSave(
                originalName = null,
                originalMode = null,
                name = "  ",
                mode = CollectionMode.STACK,
                originalGlyphId = null,
                glyphId = null,
            ),
        )
    }

    // ---- edit mode ----

    @Test
    fun `edit disables when nothing changed`() {
        assertFalse(
            collectionFormCanSave(
                originalName = "Trips",
                originalMode = CollectionMode.PACK,
                name = "Trips",
                mode = CollectionMode.PACK,
                originalGlyphId = null,
                glyphId = null,
            ),
        )
    }

    @Test
    fun `edit treats a whitespace-padded same name as unchanged`() {
        assertFalse(
            collectionFormCanSave(
                originalName = "Trips",
                originalMode = null,
                name = "  Trips ",
                mode = null,
                originalGlyphId = null,
                glyphId = null,
            ),
        )
    }

    @Test
    fun `edit enables on a name change`() {
        assertTrue(
            collectionFormCanSave(
                originalName = "Trips",
                originalMode = null,
                name = "Journeys",
                mode = null,
                originalGlyphId = null,
                glyphId = null,
            ),
        )
    }

    @Test
    fun `edit enables on a mode change alone`() {
        assertTrue(
            collectionFormCanSave(
                originalName = "Trips",
                originalMode = CollectionMode.PACK,
                name = "Trips",
                mode = CollectionMode.TRACK,
                originalGlyphId = null,
                glyphId = null,
            ),
        )
    }

    @Test
    fun `edit enables when clearing the mode to null`() {
        assertTrue(
            collectionFormCanSave(
                originalName = "Trips",
                originalMode = CollectionMode.PACK,
                name = "Trips",
                mode = null,
                originalGlyphId = null,
                glyphId = null,
            ),
        )
    }

    @Test
    fun `edit never enables with a blank name, even with a mode change`() {
        assertFalse(
            collectionFormCanSave(
                originalName = "Trips",
                originalMode = null,
                name = " ",
                mode = CollectionMode.STACK,
                originalGlyphId = null,
                glyphId = null,
            ),
        )
    }

    @Test
    fun `edit enables on a glyph change alone, including removing it`() {
        assertTrue(
            collectionFormCanSave(
                originalName = "Trips",
                originalMode = null,
                name = "Trips",
                mode = null,
                originalGlyphId = "g-1",
                glyphId = "g-2",
            ),
        )
        assertTrue(
            collectionFormCanSave(
                originalName = "Trips",
                originalMode = null,
                name = "Trips",
                mode = null,
                originalGlyphId = "g-1",
                glyphId = null,
            ),
        )
        assertFalse(
            collectionFormCanSave(
                originalName = "Trips",
                originalMode = null,
                name = "Trips",
                mode = null,
                originalGlyphId = "g-1",
                glyphId = "g-1",
            ),
        )
    }
}

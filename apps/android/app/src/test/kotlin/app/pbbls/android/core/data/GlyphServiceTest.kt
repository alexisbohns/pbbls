package app.pbbls.android.core.data

import app.pbbls.android.core.model.Glyph
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [GlyphService.withEntitled] merge contract (#562): own+system glyphs keep
 * their server order, entitled glyphs append after them, and duplicates are
 * dropped defensively.
 */
class GlyphServiceTest {
    private fun glyph(
        id: String,
        userId: String? = null,
        isSystem: Boolean = false,
    ) = Glyph(id = id, strokes = emptyList(), viewBox = "0 0 200 200", userId = userId, isSystem = isSystem)

    @Test
    fun `appends entitled glyphs after own and system`() {
        val base = listOf(glyph("own", userId = "me"), glyph("system"))
        val entitled = listOf(glyph("bought", userId = "creator"))
        assertEquals(
            listOf("own", "system", "bought"),
            GlyphService.withEntitled(base, entitled).map { it.id },
        )
    }

    @Test
    fun `drops entitled glyphs already present and de-dupes within entitled`() {
        val base = listOf(glyph("a"))
        val entitled = listOf(glyph("a"), glyph("b"), glyph("b"))
        assertEquals(
            listOf("a", "b"),
            GlyphService.withEntitled(base, entitled).map { it.id },
        )
    }

    @Test
    fun `empty entitled list leaves the base untouched`() {
        val base = listOf(glyph("a"), glyph("b"))
        assertEquals(base, GlyphService.withEntitled(base, emptyList()))
    }

    @Test
    fun `attachable keeps own and system glyphs`() {
        val rows =
            listOf(
                glyph("own", userId = "me"),
                glyph("seed", isSystem = true),
                glyph("theirs", userId = "someone"),
            )
        assertEquals(listOf("own", "seed"), GlyphService.attachable(rows, "me").map { it.id })
    }

    @Test
    fun `attachable drops an ownerless glyph that is not system`() {
        // A purged seller's SOLD glyph: user_id null, is_system false (#872).
        // Keying off `userId == null` handed it to every user for free.
        val rows = listOf(glyph("purged"), glyph("seed", isSystem = true))
        assertEquals(listOf("seed"), GlyphService.attachable(rows, "me").map { it.id })
    }

    @Test
    fun `attachable keeps server order`() {
        val rows = listOf(glyph("seed", isSystem = true), glyph("own", userId = "me"))
        assertEquals(listOf("seed", "own"), GlyphService.attachable(rows, "me").map { it.id })
    }

    @Test
    fun `attachable with a null session keeps only system glyphs`() {
        val rows = listOf(glyph("own", userId = "me"), glyph("seed", isSystem = true))
        assertEquals(listOf("seed"), GlyphService.attachable(rows, null).map { it.id })
    }
}

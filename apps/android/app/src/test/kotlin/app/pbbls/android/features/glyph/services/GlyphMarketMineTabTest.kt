package app.pbbls.android.features.glyph.services

import app.pbbls.android.core.model.MineGlyphRow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [GlyphMarketService.mineTab] ordering and membership (M43 D7, #872).
 *
 * Android deliberately keeps system glyphs pickable in Mine — a named
 * deviation from iOS's `eq(user_id, me)`. The membership test is
 * `is_system`, NOT `user_id is null`: purge_account anonymizes a SOLD glyph
 * when its creator deletes their account, and that row must stay out of every
 * stranger's Mine tab.
 */
class GlyphMarketMineTabTest {
    private fun row(
        id: String,
        userId: String? = null,
        isSystem: Boolean = false,
    ) = MineGlyphRow(
        id = id,
        strokes = emptyList(),
        viewBox = "0 0 200 200",
        userId = userId,
        isSystem = isSystem,
    )

    @Test
    fun `own creations come first, then system glyphs`() {
        val rows =
            listOf(
                row("seed", isSystem = true),
                row("own", userId = "me"),
            )
        assertEquals(listOf("own", "seed"), GlyphMarketService.mineTab(rows, "me").map { it.id })
    }

    @Test
    fun `an ownerless non-system glyph is excluded`() {
        // The purged seller's sold glyph (#872) — free to everyone before the fix.
        val rows = listOf(row("own", userId = "me"), row("purged"))
        assertEquals(listOf("own"), GlyphMarketService.mineTab(rows, "me").map { it.id })
    }

    @Test
    fun `another user's glyph is excluded`() {
        val rows = listOf(row("theirs", userId = "someone"), row("seed", isSystem = true))
        assertEquals(listOf("seed"), GlyphMarketService.mineTab(rows, "me").map { it.id })
    }
}

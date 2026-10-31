package app.pbbls.android.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.SceneStrategyScope
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphGridItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The docked sheet on compact windows (#940). */
class BottomSheetSceneStrategyTest {
    private val strategy = BottomSheetSceneStrategy<NavKey>()

    // The glyph detail is the sheet's one user since the pebble detail became a page (#1135).
    private val glyph =
        PebblesKey.GlyphDetail(
            GlyphGridItem(
                glyph = Glyph(id = "g1", strokes = emptyList(), viewBox = "0 0 200 200"),
                price = 10,
                owned = false,
                createdAt = null,
                acquiredAt = null,
            ),
        )

    private fun entry(
        key: PebblesKey,
        sheet: Boolean,
    ) = NavEntry<NavKey>(key, metadata = if (sheet) BottomSheetSceneStrategy.bottomSheet() else emptyMap()) {}

    private fun sceneFor(entries: List<NavEntry<NavKey>>) =
        with(strategy) { with(SceneStrategyScope<NavKey>()) { calculateScene(entries) } }

    @Test
    fun `a sheet entry on top becomes an overlay over what is below`() {
        val below = entry(PebblesKey.Glyphs(), sheet = false)
        val sheet = entry(glyph, sheet = true)

        val scene = sceneFor(listOf(below, sheet))

        assertTrue(scene is OverlayScene<*>)
        assertEquals(listOf(sheet), scene!!.entries)
        assertEquals(listOf(below), (scene as OverlayScene<NavKey>).overlaidEntries)
        assertEquals(listOf(below), scene.previousEntries)
        assertEquals(sheet.contentKey, scene.key)
    }

    @Test
    fun `the same entries give an equal scene`() {
        // NavDisplay keeps an overlay's composition, and so the sheet's state,
        // across recalculations only while the scenes compare equal.
        val entries = listOf(entry(PebblesKey.Glyphs(), sheet = false), entry(glyph, sheet = true))

        val first = sceneFor(entries)
        val second = sceneFor(entries)

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `anything else is left to the next strategy`() {
        assertNull(sceneFor(listOf(entry(PebblesKey.Glyphs(), sheet = false))))
    }

    @Test
    fun `a sheet entry covered by a modal is not a sheet`() {
        val scene =
            sceneFor(
                listOf(
                    entry(PebblesKey.Glyphs(), sheet = false),
                    entry(glyph, sheet = true),
                    entry(PebblesKey.Settings, sheet = false),
                ),
            )
        assertNull(scene)
    }
}

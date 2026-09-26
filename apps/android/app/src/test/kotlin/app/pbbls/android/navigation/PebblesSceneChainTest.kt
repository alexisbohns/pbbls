package app.pbbls.android.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategyScope
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphGridItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The whole chain `RootScreen` hands `NavDisplay` (#940), resolved the way
 * `NavDisplay` does: the first strategy that returns a scene wins, and null
 * from all of them means its own single pane.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class PebblesSceneChainTest {
    private fun directive(partitions: Int) =
        PaneScaffoldDirective.Default.copy(
            maxHorizontalPartitions = partitions,
            horizontalPartitionSpacerSize = if (partitions > 1) 24.dp else 0.dp,
        )

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

    private fun entry(key: PebblesKey) = NavEntry<NavKey>(key, metadata = PanePairs.metadataFor(key)) {}

    private fun resolve(
        partitions: Int,
        entries: List<NavEntry<NavKey>>,
    ): Scene<NavKey>? =
        pebblesSceneStrategies(directive(partitions)).firstNotNullOfOrNull { strategy ->
            with(strategy) { with(SceneStrategyScope<NavKey>()) { calculateScene(entries) } }
        }

    @Test
    fun `an open pebble on one pane is a sheet`() {
        val entries = listOf(PebblesKey.Path, PebblesKey.PebbleDetail("p1")).map(::entry)
        val scene = resolve(1, entries)

        assertTrue(scene is OverlayScene<*>)
        assertEquals(entries.takeLast(1), scene!!.entries)
        assertEquals(entries.take(1), (scene as OverlayScene<NavKey>).overlaidEntries)
    }

    @Test
    fun `an open pebble on two panes is the pair, not a sheet`() {
        val entries = listOf(PebblesKey.Path, PebblesKey.PebbleDetail("p1")).map(::entry)
        val scene = resolve(2, entries)

        assertFalse(scene is OverlayScene<*>)
        assertEquals(PanePair.PEBBLES, scene!!.key)
        assertEquals(entries, scene.entries)
    }

    @Test
    fun `edit over an open pebble is the default single pane on either width`() {
        val entries = listOf(PebblesKey.Path, PebblesKey.PebbleDetail("p1"), PebblesKey.EditPebble("p1")).map(::entry)

        assertNull(resolve(1, entries))
        assertNull(resolve(2, entries))
    }

    @Test
    fun `an open glyph on one pane is a sheet over the store`() {
        val entries = listOf(PebblesKey.You, PebblesKey.Glyphs, glyph).map(::entry)
        val scene = resolve(1, entries)

        assertTrue(scene is OverlayScene<*>)
        assertEquals(entries.takeLast(1), scene!!.entries)
        assertEquals(entries.take(2), (scene as OverlayScene<NavKey>).overlaidEntries)
    }

    @Test
    fun `an open glyph on two panes is the pair, not a sheet`() {
        val entries = listOf(PebblesKey.You, PebblesKey.Glyphs, glyph).map(::entry)
        val scene = resolve(2, entries)

        assertFalse(scene is OverlayScene<*>)
        assertEquals(PanePair.GLYPHS, scene!!.key)
        assertEquals(entries.drop(1), scene.entries)
    }

    @Test
    fun `the store alone on one pane is the default single pane`() {
        assertNull(resolve(1, listOf(PebblesKey.You, PebblesKey.Glyphs).map(::entry)))
    }
}

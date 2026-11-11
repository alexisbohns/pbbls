package app.pbbls.android.core.designsystem

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The five sizes a pebbles icon draws at (Android icon migration spec D6, the
 * iOS `PbblsGlyphSize` values), and the one that follows the font scale.
 */
class PbblsGlyphSizeTest {
    @Test
    fun `the five sizes, in order, from the 16 floor`() {
        assertEquals(listOf(16f, 20f, 22f, 28f, 48f), PbblsGlyphSize.entries.map { it.value })
        assertEquals(listOf(PbblsGlyphSize.INLINE), PbblsGlyphSize.entries.filter { it.followsFontScale })
    }

    @Test
    fun `at the default font scale every size is its value in dp`() {
        val density = Density(density = 2f, fontScale = 1f)
        PbblsGlyphSize.entries.forEach { size ->
            assertEquals(size.name, size.value.dp, size.toDp(density))
        }
    }

    @Test
    fun `at 200 percent inline follows the platform curve and the others hold`() {
        val doubled = Density(density = 1f, fontScale = 2f)
        // Compose applies its own copy of Android's non-linear font-scale table
        // (platform API 34+) on every API level, the JVM included: 16sp at 2x sits
        // halfway between the table's 14 -> 26 and 18 -> 30 points.
        assertEquals(28f, PbblsGlyphSize.INLINE.toDp(doubled).value, 0.01f)
        assertEquals(20.dp, PbblsGlyphSize.ROW.toDp(doubled))
        assertEquals(22.dp, PbblsGlyphSize.TOOLBAR.toDp(doubled))
        assertEquals(28.dp, PbblsGlyphSize.TILE.toDp(doubled))
        assertEquals(48.dp, PbblsGlyphSize.HERO.toDp(doubled))
    }
}

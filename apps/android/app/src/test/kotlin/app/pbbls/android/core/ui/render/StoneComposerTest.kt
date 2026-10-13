package app.pbbls.android.core.ui.render

import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The on-device stone reproduces the deleted engine's composition (`compose.ts` + `layout.ts`, git 13b5ae3b^). */
class StoneComposerTest {
    private fun seed(
        size: ValenceSizeGroup,
        polarity: ValencePolarity,
    ) = File("src/main/res/raw/shape_${size.key}_${polarity.key}.svg")

    private fun glyph(viewBox: String = "0 0 200 200") =
        PebbleGlyph(
            id = "g",
            strokes = listOf(GlyphStroke("M 20 100 Q 100 20 180 100", 6.0), GlyphStroke("M 50 50 L 150 150", 6.0)),
            viewBox = viewBox,
        )

    @Test
    fun `the bundled seeds are byte-identical to the repo seeds`() {
        ValenceSizeGroup.entries.forEach { size ->
            ValencePolarity.entries.forEach { polarity ->
                val repo = File("../../../docs/seeds/shape-seeds/${size.key}-${polarity.key}.svg")
                assertArrayEquals("$size $polarity", repo.readBytes(), seed(size, polarity).readBytes())
            }
        }
    }

    @Test
    fun `each seed keeps only its stroked paths, on the engine canvas`() {
        val expected =
            mapOf(
                (ValenceSizeGroup.SMALL to ValencePolarity.LOWLIGHT) to 1,
                (ValenceSizeGroup.SMALL to ValencePolarity.NEUTRAL) to 1,
                (ValenceSizeGroup.SMALL to ValencePolarity.HIGHLIGHT) to 1,
                (ValenceSizeGroup.MEDIUM to ValencePolarity.LOWLIGHT) to 2,
                (ValenceSizeGroup.MEDIUM to ValencePolarity.NEUTRAL) to 2,
                (ValenceSizeGroup.MEDIUM to ValencePolarity.HIGHLIGHT) to 3,
                (ValenceSizeGroup.LARGE to ValencePolarity.LOWLIGHT) to 2,
                (ValenceSizeGroup.LARGE to ValencePolarity.NEUTRAL) to 3,
                (ValenceSizeGroup.LARGE to ValencePolarity.HIGHLIGHT) to 7,
            )
        val canvas =
            mapOf(
                ValenceSizeGroup.SMALL to (250f to 200f),
                ValenceSizeGroup.MEDIUM to (260f to 260f),
                ValenceSizeGroup.LARGE to (260f to 310f),
            )
        expected.forEach { (key, paths) ->
            val model = StoneComposer.shapeModel(seed(key.first, key.second).readText())!!
            val layer = model.layers.single()
            assertEquals("$key", PebbleSvgModel.Layer.Kind.SHAPE, layer.kind)
            assertEquals("$key", paths, layer.paths.size)
            assertEquals("$key", canvas.getValue(key.first), model.viewBox.width to model.viewBox.height)
        }
    }

    @Test
    fun `every size and polarity has the engine's glyph slot`() {
        val slots =
            mapOf(
                (ValenceSizeGroup.SMALL to ValencePolarity.HIGHLIGHT) to GlyphSlot(37.5f, 30f, 140f),
                (ValenceSizeGroup.SMALL to ValencePolarity.NEUTRAL) to GlyphSlot(25f, 30f, 140f),
                (ValenceSizeGroup.SMALL to ValencePolarity.LOWLIGHT) to GlyphSlot(30f, 30f, 140f),
                (ValenceSizeGroup.MEDIUM to ValencePolarity.HIGHLIGHT) to GlyphSlot(26f, 84f, 150f),
                (ValenceSizeGroup.MEDIUM to ValencePolarity.NEUTRAL) to GlyphSlot(26f, 55f, 150f),
                (ValenceSizeGroup.MEDIUM to ValencePolarity.LOWLIGHT) to GlyphSlot(39f, 26f, 150f),
                (ValenceSizeGroup.LARGE to ValencePolarity.HIGHLIGHT) to GlyphSlot(50f, 75f, 160f),
                (ValenceSizeGroup.LARGE to ValencePolarity.NEUTRAL) to GlyphSlot(26f, 88f, 160f),
                (ValenceSizeGroup.LARGE to ValencePolarity.LOWLIGHT) to GlyphSlot(26f, 75f, 160f),
            )
        slots.forEach { (key, slot) -> assertEquals("$key", slot, StoneComposer.glyphSlot(key.first, key.second)) }
    }

    @Test
    fun `a 200 box glyph lands in its slot at the engine's scale`() {
        val shape = StoneComposer.shapeModel(seed(ValenceSizeGroup.MEDIUM, ValencePolarity.NEUTRAL).readText())!!
        val stone = StoneComposer.compose(shape, ValenceSizeGroup.MEDIUM, ValencePolarity.NEUTRAL, glyph())
        val layer = stone.layers.last()
        assertEquals(PebbleSvgModel.Layer.Kind.GLYPH, layer.kind)
        assertEquals(Affine(0.75f, 0f, 0f, 0.75f, 26f, 55f), layer.transform)
        assertEquals(listOf("M 20 100 Q 100 20 180 100", "M 50 50 L 150 150"), layer.paths.map { it.d })
    }

    @Test
    fun `an offset, non-square view box is fitted and shifted into the slot`() {
        val shape = StoneComposer.shapeModel(seed(ValenceSizeGroup.MEDIUM, ValencePolarity.NEUTRAL).readText())!!
        val layer = StoneComposer.compose(shape, ValenceSizeGroup.MEDIUM, ValencePolarity.NEUTRAL, glyph("-10 -10 400 200")).layers.last()
        assertEquals(Affine(0.375f, 0f, 0f, 0.375f, 29.75f, 58.75f), layer.transform)
    }

    @Test
    fun `no glyph, or an unreadable view box, leaves the bare shape`() {
        val shape = StoneComposer.shapeModel(seed(ValenceSizeGroup.SMALL, ValencePolarity.LOWLIGHT).readText())!!
        assertSame(shape, StoneComposer.compose(shape, ValenceSizeGroup.SMALL, ValencePolarity.LOWLIGHT, null))
        assertSame(shape, StoneComposer.compose(shape, ValenceSizeGroup.SMALL, ValencePolarity.LOWLIGHT, glyph("garbage")))
    }

    @Test
    fun `the cache key follows the glyph`() {
        val a = StoneComposer.cacheKey(ValenceSizeGroup.SMALL, ValencePolarity.LOWLIGHT, glyph())
        val b = StoneComposer.cacheKey(ValenceSizeGroup.SMALL, ValencePolarity.LOWLIGHT, null)
        assertNotEquals(a, b)
        assertNotEquals(StoneComposer.BLANK_CACHE_KEY, StoneComposer.cacheKey(ValenceSizeGroup.MEDIUM, ValencePolarity.NEUTRAL, null))
    }

    @Test
    fun `the blank stone keeps only the closed silhouette outline`() {
        val shape = StoneComposer.shapeModel(seed(ValenceSizeGroup.MEDIUM, ValencePolarity.NEUTRAL).readText())!!
        val paths =
            StoneComposer
                .outlineOnly(shape)
                .layers
                .single()
                .paths
        assertEquals(1, paths.size)
        assertTrue(
            paths
                .single()
                .d
                .trimEnd()
                .endsWith("Z"),
        )
    }
}

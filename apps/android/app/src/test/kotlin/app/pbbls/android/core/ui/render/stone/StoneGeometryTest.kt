package app.pbbls.android.core.ui.render.stone

import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.ui.render.Affine
import app.pbbls.android.core.ui.render.PebbleOutlineGeometry
import app.pbbls.android.core.ui.render.StoneComposer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The expected numbers are the web's, produced once from `apps/web` with
 * `npx tsx -e 'import * as g from "./lib/config/stone-geometry"; …'` and
 * pasted as the web prints them (rounded to 3 decimals).
 */
class StoneGeometryTest {
    private fun outline(
        size: ValenceSizeGroup,
        polarity: ValencePolarity,
    ): String = outlinePathData(File("src/main/res/raw/outline_${size.key}_${polarity.key}.svg").readText())

    private fun assertAffine(
        label: String,
        a: Double,
        d: Double,
        e: Double,
        f: Double,
        actual: Affine,
        tolerance: Double = 0.002,
    ) {
        assertEquals("$label a", a, actual.a.toDouble(), tolerance)
        assertEquals("$label b", 0.0, actual.b.toDouble(), 0.0)
        assertEquals("$label c", 0.0, actual.c.toDouble(), 0.0)
        assertEquals("$label d", d, actual.d.toDouble(), tolerance)
        assertEquals("$label e", e, actual.e.toDouble(), tolerance)
        assertEquals("$label f", f, actual.f.toDouble(), tolerance)
    }

    @Test
    fun `glyph zones are the composer's slots`() {
        ValenceSizeGroup.entries.forEach { size ->
            ValencePolarity.entries.forEach { polarity ->
                val slot = StoneComposer.glyphSlot(size, polarity)
                val zone = StoneGeometry.glyphZone(size, polarity)
                assertEquals("$size $polarity", StoneGeometry.GlyphZone(slot.x, slot.y, slot.size), zone)
            }
        }
    }

    @Test
    fun `outline units per on-screen px`() {
        assertEquals(337.0 / 34.0, StoneGeometry.unitsPerPx(ValenceSizeGroup.SMALL).toDouble(), 1e-4)
        assertEquals(350.0 / 44.0, StoneGeometry.unitsPerPx(ValenceSizeGroup.MEDIUM).toDouble(), 1e-4)
        assertEquals(335.0 / 54.0, StoneGeometry.unitsPerPx(ValenceSizeGroup.LARGE).toDouble(), 1e-4)
    }

    @Test
    fun `the outline box is the backdrop's view box`() {
        assertEquals(StoneGeometry.Box(337f, 270f), StoneGeometry.outlineBox(ValenceSizeGroup.SMALL))
        assertEquals(StoneGeometry.Box(350f, 350f), StoneGeometry.outlineBox(ValenceSizeGroup.MEDIUM))
        assertEquals(StoneGeometry.Box(335f, 400f), StoneGeometry.outlineBox(ValenceSizeGroup.LARGE))
    }

    @Test
    fun `canvas scale fits the seed canvas into the outline box`() {
        assertEquals(1.3461538461538463, StoneGeometry.canvasScale(ValenceSizeGroup.MEDIUM).toDouble(), 1e-5)
    }

    @Test
    fun `canvas transform centres the fitted canvas`() {
        assertAffine(
            "canvas",
            1.346,
            1.346,
            0.0,
            0.0,
            StoneGeometry.canvasTransform(ValenceSizeGroup.MEDIUM, ValencePolarity.HIGHLIGHT),
        )
    }

    @Test
    fun `carve transform is the engine inset times the carving scale`() {
        val carve = StoneGeometry.carveTransform(ValenceSizeGroup.MEDIUM, ValencePolarity.LOWLIGHT)
        assertAffine("carve", 1.22, 1.22, 16.4, 16.4, carve)
        val expectedScale =
            StoneGeometry.canvasScale(ValenceSizeGroup.MEDIUM) *
                PebbleOutlineGeometry.pebbleScale(ValenceSizeGroup.MEDIUM) *
                StoneGeometry.CARVE_SCALE
        assertEquals(expectedScale.toDouble(), carve.a.toDouble(), 1e-5)
    }

    @Test
    fun `glyph transform places the glyph in its zone`() {
        assertAffine(
            "glyph",
            0.7,
            0.7,
            25.0,
            30.0,
            StoneGeometry.glyphTransform(ValenceSizeGroup.SMALL, ValencePolarity.NEUTRAL, 200f),
        )
    }

    @Test
    fun `silhouette frame is the outer contour's centroid and box`() {
        val small = StoneGeometry.silhouetteFrame(outline(ValenceSizeGroup.SMALL, ValencePolarity.NEUTRAL))
        assertEquals(162.09358985417893, small.cx.toDouble(), 0.01)
        assertEquals(139.1140773347425, small.cy.toDouble(), 0.01)
        assertEquals(304.9967810570988, small.width.toDouble(), 0.01)
        assertEquals(246.99617379674973, small.height.toDouble(), 0.01)

        // large-lowlight has a hole as its second contour; the frame ignores it.
        val large = StoneGeometry.silhouetteFrame(outline(ValenceSizeGroup.LARGE, ValencePolarity.LOWLIGHT))
        assertEquals(157.92152813435214, large.cx.toDouble(), 0.01)
        assertEquals(196.36803073996094, large.cy.toDouble(), 0.01)
        assertEquals(319.99817198421874, large.width.toDouble(), 0.01)
        assertEquals(360.0751259097222, large.height.toDouble(), 0.01)
    }

    @Test
    fun `small neutral draws one ring and a halo`() {
        val geometry =
            StoneGeometry.ringGeometry(
                ValenceSizeGroup.SMALL,
                ValencePolarity.NEUTRAL,
                outline(ValenceSizeGroup.SMALL, ValencePolarity.NEUTRAL),
            )
        assertEquals(1, geometry.rings.size)
        assertAffine("ring 1", 1.312, 1.385, -50.57, -53.592, geometry.rings[0])
        assertAffine("halo", 1.555, 1.686, -90.015, -95.394, geometry.halo)
    }

    @Test
    fun `large lowlight draws three rings and a halo`() {
        val geometry =
            StoneGeometry.ringGeometry(
                ValenceSizeGroup.LARGE,
                ValencePolarity.LOWLIGHT,
                outline(ValenceSizeGroup.LARGE, ValencePolarity.LOWLIGHT),
            )
        assertEquals(3, geometry.rings.size)
        assertAffine("ring 1", 1.163, 1.145, -25.717, -28.419, geometry.rings[0])
        assertAffine("ring 2", 1.326, 1.289, -51.434, -56.838, geometry.rings[1])
        assertAffine("ring 3", 1.489, 1.434, -77.152, -85.257, geometry.rings[2])
        assertAffine("halo", 1.616, 1.547, -97.211, -107.424, geometry.halo)
    }

    @Test
    fun `ring geometry is cached per size and polarity`() {
        val path = outline(ValenceSizeGroup.MEDIUM, ValencePolarity.NEUTRAL)
        val first = StoneGeometry.ringGeometry(ValenceSizeGroup.MEDIUM, ValencePolarity.NEUTRAL, path)
        val second = StoneGeometry.ringGeometry(ValenceSizeGroup.MEDIUM, ValencePolarity.NEUTRAL, path)
        assertEquals(true, first === second)
        assertEquals(StoneGeometry.RING_COUNT.getValue(ValenceSizeGroup.MEDIUM), first.rings.size)
    }

    @Test
    fun `glyph side reads the view box, 200 when unreadable`() {
        assertEquals(200f, StoneGeometry.glyphSide("0 0 200 200"))
        assertEquals(200f, StoneGeometry.glyphSide("garbage"))
        assertEquals(160f, StoneGeometry.glyphSide("0 0 160 160"))
        assertEquals(160f, StoneGeometry.glyphSide("0,0,160,160"))
    }

    @Test
    fun `outline path data is the asset's single path`() {
        val raw = File("src/main/res/raw/outline_small_neutral.svg").readText()
        val d = outlinePathData(raw)
        assertEquals(true, d.startsWith("M97.3622 12.4435C113.519"))
        assertEquals(true, d.endsWith("Z"))
        assertEquals("", outlinePathData("<svg id=\"x\"></svg>"))
    }

    @Test
    fun `the constants are the mock's`() {
        assertEquals(3.86f to 6.95f, StoneGeometry.SHADOW_FAR_PX)
        assertEquals(0.93f to 2.01f, StoneGeometry.SHADOW_NEAR_PX)
        assertEquals(0.32f to 0.54f, StoneGeometry.LIP_PX)
        assertEquals(5f, StoneGeometry.Blur.NEAR)
        assertEquals(15f, StoneGeometry.Blur.FAR)
        assertEquals(7f, StoneGeometry.Blur.AO)
        assertEquals(12f, StoneGeometry.Blur.SOFT)
        assertEquals(4f, StoneGeometry.Blur.GLOW)
        assertEquals(
            mapOf(ValenceSizeGroup.SMALL to 1, ValenceSizeGroup.MEDIUM to 2, ValenceSizeGroup.LARGE to 3),
            StoneGeometry.RING_COUNT,
        )
        assertEquals(1.22f, StoneGeometry.CARVE_SCALE)
        assertEquals(1.25f, StoneGeometry.CARVE_STROKE_PX)
        assertEquals(
            mapOf(ValenceSizeGroup.SMALL to 34f, ValenceSizeGroup.MEDIUM to 44f, ValenceSizeGroup.LARGE to 54f),
            StoneGeometry.STONE_WIDTH_DP,
        )
        assertEquals(
            mapOf(
                ValenceSizeGroup.SMALL to StoneGeometry.Box(250f, 200f),
                ValenceSizeGroup.MEDIUM to StoneGeometry.Box(260f, 260f),
                ValenceSizeGroup.LARGE to StoneGeometry.Box(260f, 310f),
            ),
            StoneGeometry.SEED_CANVAS,
        )
    }
}

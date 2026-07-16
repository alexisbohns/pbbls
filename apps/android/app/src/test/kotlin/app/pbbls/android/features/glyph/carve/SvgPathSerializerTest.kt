package app.pbbls.android.features.glyph.carve

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [SvgPathSerializer] — byte parity with the iOS/web serializer (M43 D1).
 * The four canonical vectors come verbatim from the iOS suite; the rest pin
 * the number-formatting rules Kotlin would otherwise get wrong.
 */
class SvgPathSerializerTest {
    private fun p(
        x: Double,
        y: Double,
    ) = CarvePoint(x, y)

    @Test
    fun `empty input serializes to an empty string`() {
        assertEquals("", SvgPathSerializer.svgPathString(emptyList()))
    }

    @Test
    fun `a single tap serializes as a zero-length line`() {
        assertEquals("M10,20 L10,20", SvgPathSerializer.svgPathString(listOf(p(10.0, 20.0))))
    }

    @Test
    fun `two points serialize as a plain line`() {
        assertEquals("M0,0 L10,20", SvgPathSerializer.svgPathString(listOf(p(0.0, 0.0), p(10.0, 20.0))))
    }

    @Test
    fun `three points serialize as a quadratic through the midpoint`() {
        assertEquals(
            "M0,0 Q10,10 15,15 L20,20",
            SvgPathSerializer.svgPathString(listOf(p(0.0, 0.0), p(10.0, 10.0), p(20.0, 20.0))),
        )
    }

    @Test
    fun `fractional values round to two decimals and strip trailing zeros`() {
        assertEquals(
            "M0.12,0.99 L10,5.11",
            SvgPathSerializer.svgPathString(listOf(p(0.123, 0.987), p(9.999, 5.111))),
        )
    }

    @Test
    fun `whole numbers never print a decimal point`() {
        assertEquals("M200,0 L100,50", SvgPathSerializer.svgPathString(listOf(p(200.0, 0.0), p(100.0, 50.0))))
    }

    @Test
    fun `rounding happens before the whole-number check`() {
        // 9.999 → 10 (integer form), 0.995 → 1 — ties round away from zero.
        assertEquals("M10,1 L10,1", SvgPathSerializer.svgPathString(listOf(p(9.999, 0.995))))
    }

    @Test
    fun `negative coordinates keep their sign in both forms`() {
        assertEquals("M-3,-0.25 L0,0", SvgPathSerializer.svgPathString(listOf(p(-3.0, -0.251), p(0.0, 0.0))))
    }

    @Test
    fun `four points chain two quadratics before the closing line`() {
        assertEquals(
            "M0,0 Q10,0 15,5 Q20,10 25,15 L30,20",
            SvgPathSerializer.svgPathString(
                listOf(p(0.0, 0.0), p(10.0, 0.0), p(20.0, 10.0), p(30.0, 20.0)),
            ),
        )
    }
}

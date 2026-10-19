package app.pbbls.android.core.ui.render.stone

import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The seeds split the way the web (`stone-carvings.test.ts`) and iOS (`StoneShapeParserTests`) split them. */
class StoneCarvingsTest {
    private fun seed(
        size: ValenceSizeGroup,
        polarity: ValencePolarity,
    ): String = File("src/main/res/raw/shape_${size.key}_${polarity.key}.svg").readText()

    private val veinCounts =
        mapOf(
            (ValenceSizeGroup.SMALL to ValencePolarity.LOWLIGHT) to 0,
            (ValenceSizeGroup.SMALL to ValencePolarity.NEUTRAL) to 0,
            (ValenceSizeGroup.SMALL to ValencePolarity.HIGHLIGHT) to 0,
            (ValenceSizeGroup.MEDIUM to ValencePolarity.LOWLIGHT) to 1,
            (ValenceSizeGroup.MEDIUM to ValencePolarity.NEUTRAL) to 1,
            (ValenceSizeGroup.MEDIUM to ValencePolarity.HIGHLIGHT) to 2,
            (ValenceSizeGroup.LARGE to ValencePolarity.LOWLIGHT) to 1,
            (ValenceSizeGroup.LARGE to ValencePolarity.NEUTRAL) to 2,
            (ValenceSizeGroup.LARGE to ValencePolarity.HIGHLIGHT) to 6,
        )

    private val canvases =
        mapOf(
            ValenceSizeGroup.SMALL to (250f to 200f),
            ValenceSizeGroup.MEDIUM to (260f to 260f),
            ValenceSizeGroup.LARGE to (260f to 310f),
        )

    @Test
    fun `every seed parses on its engine canvas`() {
        ValenceSizeGroup.entries.forEach { size ->
            ValencePolarity.entries.forEach { polarity ->
                val carving = StoneCarvings.parse(seed(size, polarity))
                assertNotNull("$size $polarity", carving)
                assertEquals("$size $polarity", canvases.getValue(size), carving!!.width to carving.height)
            }
        }
    }

    @Test
    fun `veins count 0-0-0, 1-1-2 and 1-2-6, lowlight to highlight`() {
        veinCounts.forEach { (key, expected) ->
            val carving = StoneCarvings.parse(seed(key.first, key.second))!!
            assertEquals("$key", expected, carving.veins.size)
            carving.veins.forEach { assertTrue("$key vein is not empty", it.isNotBlank()) }
        }
    }

    @Test
    fun `every seed but large highlight carries an even-odd fossil`() {
        ValenceSizeGroup.entries.forEach { size ->
            ValencePolarity.entries.forEach { polarity ->
                val fossil = StoneCarvings.parse(seed(size, polarity))!!.fossil
                if (size == ValenceSizeGroup.LARGE && polarity == ValencePolarity.HIGHLIGHT) {
                    assertNull("$size $polarity", fossil)
                } else {
                    assertNotNull("$size $polarity", fossil)
                    assertTrue("$size $polarity fill-rule", fossil!!.evenOdd)
                    assertTrue("$size $polarity fossil is not empty", fossil.d.isNotBlank())
                }
            }
        }
    }

    @Test
    fun `large neutral's outline is the third stroked path, by extent not by position`() {
        val classification = StoneCarvings.classify(seed(ValenceSizeGroup.LARGE, ValencePolarity.NEUTRAL))!!
        assertEquals(2, classification.outlineIndex)
        assertEquals(listOf(0, 1), classification.veinIndices)
        assertEquals(3, classification.fossilIndex)
    }

    @Test
    fun `fill-rule decides the fossil's even-odd flag`() {
        val svg =
            """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <path d="M 0 0 L 100 0 L 100 100 L 0 100 Z" fill="none" stroke="black" stroke-width="6"/>
              <path d="M 10 10 L 30 10 L 30 30 Z" fill="none" stroke="black" stroke-width="6"/>
              <path fill-rule="nonzero" d="M 40 40 L 60 40 L 60 60 Z" fill="black"/>
            </svg>
            """.trimIndent()
        val carving = StoneCarvings.parse(svg)!!
        assertEquals(1, carving.veins.size)
        assertEquals("M 10 10 L 30 10 L 30 30 Z", carving.veins.single())
        assertEquals(StoneFossil("M 40 40 L 60 40 L 60 60 Z", evenOdd = false), carving.fossil)
    }

    @Test
    fun `an outline-only seed has no veins and no fossil`() {
        val svg =
            """
            <svg viewBox="0 0 100 100">
              <path d="M 0 0 L 100 0 L 100 100 L 0 100 Z" stroke="black" stroke-width="6"/>
            </svg>
            """.trimIndent()
        assertEquals(StoneCarving(100f, 100f, emptyList(), null), StoneCarvings.parse(svg))
    }

    @Test
    fun `no viewBox or no stroked path parses to null`() {
        assertNull(StoneCarvings.parse("<svg/>"))
        assertNull(StoneCarvings.parse("""<svg viewBox="0 0 100 100"><path d="M 0 0 L 1 1" fill="black"/></svg>"""))
        assertNull(StoneCarvings.parse("""<svg><path d="M 0 0 L 1 1" stroke="black"/></svg>"""))
        assertNull(StoneCarvings.parse("not xml"))
    }
}

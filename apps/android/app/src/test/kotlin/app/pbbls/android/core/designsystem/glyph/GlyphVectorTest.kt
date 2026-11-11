package app.pbbls.android.core.designsystem.glyph

import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.VectorPath
import app.pbbls.android.core.designsystem.PbblsGlyph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GlyphVectorTest {
    @Before
    fun install() = GlyphTestData.install()

    private fun paths(
        glyph: PbblsGlyph,
        form: GlyphForm,
    ): List<VectorPath> =
        GlyphCatalog
            .vector(glyph, form)
            .root
            .toList()
            .filterIsInstance<VectorPath>()

    @Test
    fun aLineIsStrokedOnlyOnTheTwentyFourSquare() {
        val vector = GlyphCatalog.vector(PbblsGlyph.HOME, GlyphForm.REGULAR)
        assertEquals(24f, vector.viewportWidth)
        val first = paths(PbblsGlyph.HOME, GlyphForm.REGULAR).first()
        assertNull(first.fill)
        assertNotNull(first.stroke)
        assertEquals(1.75f, first.strokeLineWidth)
    }

    @Test
    fun aSolidStartsWithItsCarvedFillEvenOddAndUnstroked() {
        val carved = paths(PbblsGlyph.PEBBLE_ROUND, GlyphForm.SOLID).first()
        assertEquals(PathFillType.EvenOdd, carved.pathFillType)
        assertNotNull(carved.fill)
        assertNull(carved.stroke)
    }

    @Test
    fun cachesOneVectorPerGlyphAndForm() {
        val a = GlyphCatalog.vector(PbblsGlyph.STACK, GlyphForm(GlyphFill.Progress(2)))
        assertSame(a, GlyphCatalog.vector(PbblsGlyph.STACK, GlyphForm(GlyphFill.Progress(2))))
        assertTrue(a !== GlyphCatalog.vector(PbblsGlyph.STACK, GlyphForm(GlyphFill.Progress(3))))
    }
}

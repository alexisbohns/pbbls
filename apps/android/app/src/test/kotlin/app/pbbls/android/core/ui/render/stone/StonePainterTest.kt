package app.pbbls.android.core.ui.render.stone

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color.alpha
import android.graphics.Color.blue
import android.graphics.Color.green
import android.graphics.Color.red
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The painter draws into a real bitmap (Robolectric's native graphics, so
 * `BlurMaskFilter` and the shaders rasterise as on a device) and the test
 * reads pixels: inside the silhouette, outside it, grey when blank, dimmed
 * at night, byte-identical twice over.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StonePainterTest {
    private val resources get() = ApplicationProvider.getApplicationContext<Context>().resources

    private val pride =
        requireNotNull(
            EmotionPalette.fromHex(
                primaryHex = "#7B5E99FF",
                secondaryHex = "#AE91CCFF",
                lightHex = "#F2EFF5FF",
                surfaceHex = "#7B5E991A",
                darkHex = "#2A2138FF",
                shadedHex = "#4A3A5CFF",
            ),
        ).toStonePalette()

    private val glyph =
        PebbleGlyph(
            id = "glyph-wave",
            strokes =
                listOf(
                    GlyphStroke(d = "M 40 100 L 160 100", width = 6.0),
                    GlyphStroke(d = "M 100 40 Q 120 100 100 160", width = 4.0),
                ),
            viewBox = "0 0 200 200",
        )

    private fun spec(
        size: ValenceSizeGroup = ValenceSizeGroup.MEDIUM,
        polarity: ValencePolarity = ValencePolarity.HIGHLIGHT,
        palette: StonePalette? = pride,
        glyph: PebbleGlyph? = this.glyph,
        seed: Long = 0x5EEDL,
        night: Boolean = false,
    ) = StoneSpec(
        size = size,
        polarity = polarity,
        palette = palette,
        glyph = glyph,
        seed = seed,
        shadow = Color.Black,
        shadowAlpha = 0.3f,
        night = night,
    )

    private fun paint(spec: StoneSpec): Bitmap {
        val bitmap = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ARGB_8888)
        StonePainter.paint(spec, Canvas(bitmap), SIDE, SIDE, resources)
        return bitmap
    }

    private fun Bitmap.centre(): Int = getPixel(width / 2, height / 2)

    @Before
    fun clearCache() = StoneBitmapCache.clear()

    @Test
    fun `the stone fills its centre and leaves the corner clear`() {
        val bitmap = paint(spec())

        assertEquals(255, alpha(bitmap.centre()))
        assertEquals(0, alpha(bitmap.getPixel(0, 0)))
    }

    @Test
    fun `every polarity and size paints without throwing and covers its centre`() {
        ValenceSizeGroup.entries.forEach { size ->
            ValencePolarity.entries.forEach { polarity ->
                val bitmap = paint(spec(size = size, polarity = polarity))
                assertEquals("$size $polarity", 255, alpha(bitmap.centre()))
            }
        }
    }

    @Test
    fun `painting the same spec twice is byte-identical`() {
        val first = paint(spec())
        val second = paint(spec())

        assertTrue(first.sameAs(second))
    }

    @Test
    fun `a blank moment is a grey pebble`() {
        val centre = paint(spec(palette = null, polarity = ValencePolarity.LOWLIGHT)).centre()

        assertEquals(255, alpha(centre))
        assertTrue("$centre is not grey", abs(red(centre) - green(centre)) <= 2 && abs(green(centre) - blue(centre)) <= 2)
    }

    @Test
    fun `night changes the stone`() {
        val paper = paint(spec(night = false)).centre()
        val night = paint(spec(night = true)).centre()

        assertNotEquals(paper, night)
    }

    @Test
    fun `the cache hands back the same bitmap for the same spec and another for another seed`() {
        val first = StoneBitmapCache.get(spec(), SIDE, SIDE, DENSITY, resources)
        val again = StoneBitmapCache.get(spec(), SIDE, SIDE, DENSITY, resources)
        val other = StoneBitmapCache.get(spec(seed = 0xBEEFL), SIDE, SIDE, DENSITY, resources)

        assertSame(first, again)
        assertNotSame(first, other)
        assertNotEquals(
            StoneBitmapCache.key(spec(), SIDE, SIDE, DENSITY),
            StoneBitmapCache.key(spec(seed = 0xBEEFL), SIDE, SIDE, DENSITY),
        )
    }

    @Test
    fun `the shadow fades out inside the bitmap, which grows past the box`() {
        ValenceSizeGroup.entries.forEach { size ->
            ValencePolarity.entries.forEach { polarity ->
                listOf(false, true).forEach { night ->
                    val painted = StonePainter.render(spec(size = size, polarity = polarity, night = night), SIDE, SIDE, resources)
                    val bitmap = painted.image.asAndroidBitmap()
                    val margin = painted.margin
                    val label = "$size $polarity night=$night $margin"

                    assertEquals(label, SIDE + margin.left + margin.right, bitmap.width)
                    assertEquals(label, SIDE + margin.top + margin.bottom, bitmap.height)
                    // The light is top-left: the shadow reaches further right and down.
                    assertTrue(label, margin.right > margin.left && margin.bottom > margin.top)
                    val edge =
                        (0 until bitmap.width).flatMap { x -> listOf(bitmap.getPixel(x, 0), bitmap.getPixel(x, bitmap.height - 1)) } +
                            (0 until bitmap.height).flatMap { y -> listOf(bitmap.getPixel(0, y), bitmap.getPixel(bitmap.width - 1, y)) }
                    assertTrue("$label edge alpha ${edge.maxOf { alpha(it) }}", edge.all { alpha(it) <= 1 })
                }
            }
        }
    }

    @Test
    fun `the padded bitmap holds the box where the plain paint draws it`() {
        val plain = paint(spec())
        val painted = StonePainter.render(spec(), SIDE, SIDE, resources)
        val bitmap = painted.image.asAndroidBitmap()

        assertEquals(plain.centre(), bitmap.getPixel(painted.margin.left + SIDE / 2, painted.margin.top + SIDE / 2))
    }

    @Test
    fun `the same pixel size at another density is another key`() {
        assertNotEquals(StoneBitmapCache.key(spec(), SIDE, SIDE, DENSITY), StoneBitmapCache.key(spec(), SIDE, SIDE, 2f))
    }

    private companion object {
        /** 44 dp medium stone at 3× density. */
        const val SIDE = 132
        const val DENSITY = 3f
    }
}

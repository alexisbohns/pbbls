package app.pbbls.android.features.path.card

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.hypot

/**
 * The stamp rasterises for real (Robolectric's native graphics) on a black ground in
 * black ink, so every non-black pixel is the paper noise. A guard on the noise's
 * tint, clip and restraint, not a proof of HWUI: the device is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DomainPostmarkPixelTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the noise pits the ink inside the ring and stays restrained`() {
        compose.setContent {
            PebblesTheme {
                DomainPostmark(
                    name = "Friends",
                    strokes = null,
                    viewBox = null,
                    ink = Color.Black,
                    paper = Color.White,
                    seed = 1L,
                    diameter = 72.dp,
                    modifier = Modifier.testTag("pm").background(Color.Black),
                )
            }
        }
        val pixels = compose.onNodeWithTag("pm").captureToImage().toPixelMap()
        val c = pixels.width / 2f
        val r = pixels.width / 2f
        var pits = 0
        var inside = 0
        var light = 0f
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                if (hypot(x + 0.5f - c, y + 0.5f - c) < r * 0.9f) {
                    val red = pixels[x, y].red
                    inside++
                    light += red
                    if (red > 0.5f) pits++
                }
            }
        }
        assertTrue("expected pits inside the ring, got $pits", pits > 0)
        assertTrue("the paper should show through rarely: mean ${light / inside}", light / inside < 0.2f)
        // The noise is clipped to the disc: the square's corners stay ground.
        assertEquals(0f, pixels[1, 1].red, 0.01f)
        assertEquals(0f, pixels[pixels.width - 2, pixels.height - 2].red, 0.01f)
    }

    @Test
    fun `capital accents print inside the ring band on a shrunk stamp`() {
        val diameter = 61.5.dp
        compose.setContent {
            PebblesTheme {
                Row {
                    listOf("ÉÊÉ" to "accented", "EEE" to "bare").forEach { (name, tag) ->
                        DomainPostmark(
                            name = name,
                            strokes = null,
                            viewBox = null,
                            ink = Color.Black,
                            paper = Color.White,
                            seed = 1L,
                            diameter = diameter,
                            modifier = Modifier.testTag(tag).background(Color.White),
                        )
                    }
                }
            }
        }
        val density = compose.density.density
        val r = diameter.value / 2
        val baseline = r - CardLayout.POSTMARK_TEXT_INSET.value
        val scale = diameter / CardLayout.POSTMARK_DIAMETER
        val ringInner = r - (CardLayout.POSTMARK_OUTER_RING_INSET.value + CardLayout.POSTMARK_OUTER_RING.value / 2) * scale
        // Above Ysabeau's cap height (0.66 em at 8.5 sp, 5.6 dp) and short of the outer ring: only accents print here.
        val band = (baseline + 6f) * density..(ringInner - 0.4f) * density
        val accented = inkIn("accented", band)
        val bare = inkIn("bare", band)
        // Bare capitals leave the band empty; a scaled-down band put their tops in it and the accents in the ring.
        assertTrue("accents should print in the band: accented $accented, bare $bare", accented > bare + 10 && bare * 4 < accented)
    }

    @Test
    fun `the tallest accent clears the outer ring at both diameters`() {
        // Ysabeau's É and Ê reach 0.907 em above the baseline; the ring type is at most 8.5 sp.
        val accentTop = 0.907f * 8.5f
        for (diameter in listOf(72f, 61.5f)) {
            val r = diameter / 2
            val scale = diameter / CardLayout.POSTMARK_DIAMETER.value
            val ringInner = r - (CardLayout.POSTMARK_OUTER_RING_INSET.value + CardLayout.POSTMARK_OUTER_RING.value / 2) * scale
            val baseline = r - CardLayout.POSTMARK_TEXT_INSET.value
            assertTrue(
                "at $diameter dp the accent tops at ${baseline + accentTop}, the ring starts at $ringInner",
                baseline + accentTop < ringInner,
            )
            val innerRing = baseline - CardLayout.POSTMARK_INNER_RING_DROP.value
            val glyphRadius = CardLayout.POSTMARK_GLYPH.value * scale / 2
            assertTrue("at $diameter dp the inner ring $innerRing should clear the glyph $glyphRadius", innerRing > glyphRadius)
        }
    }

    /** Dark pixels of the stamp tagged [tag] whose distance from its centre falls in [band], in px. */
    private fun inkIn(
        tag: String,
        band: ClosedFloatingPointRange<Float>,
    ): Int {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        val c = pixels.width / 2f
        var n = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                if (hypot(x + 0.5f - c, y + 0.5f - c) in band && pixels[x, y].red < 0.5f) n++
            }
        }
        return n
    }
}

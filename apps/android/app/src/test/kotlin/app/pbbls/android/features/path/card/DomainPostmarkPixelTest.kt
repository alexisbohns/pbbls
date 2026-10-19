package app.pbbls.android.features.path.card

import android.app.Application
import androidx.compose.foundation.background
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
}

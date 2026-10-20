package app.pbbls.android.features.path.spine

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.testing.PIXEL_TEST_SDK
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The ground rasterises for real (Robolectric's native graphics) and the test reads pixels:
 * the rake is full-strength in the margin and fades past the content column, as the web's
 * mask has it — a layer or blend-mode slip would leave the grooves at one strength across.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpineGroundTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the rake fades past the content column`() {
        compose.setContent {
            PebblesTheme {
                // White, so only the shadow strokes register and the lit ones vanish.
                Box(Modifier.size(390.dp, 200.dp).background(Color.White)) {
                    SpineGround(Modifier.size(390.dp, 200.dp).testTag("ground"))
                }
            }
        }
        val image = compose.onNodeWithTag("ground").captureToImage()
        val density = compose.density.density

        val margin = darkness(image, xDp = 20f, density)
        val column = darkness(image, xDp = 300f, density)
        assertTrue("margin $margin should carry the rake", margin > 0.01f)
        assertTrue("column $column should be well under the margin $margin", column < margin * 0.45f)
    }

    /** Mean darkness (1 − luminance) over a 36-dp-wide, full-height band from [xDp]: how much rake is there. */
    private fun darkness(
        image: ImageBitmap,
        xDp: Float,
        density: Float,
    ): Float {
        val pixels = image.toPixelMap()
        val x0 = (xDp * density).toInt()
        val x1 = x0 + (36 * density).toInt()
        var sum = 0f
        var n = 0
        for (y in 0 until pixels.height) {
            for (x in x0 until x1) {
                sum += 1f - pixels[x, y].luminance()
                n++
            }
        }
        return sum / n
    }
}

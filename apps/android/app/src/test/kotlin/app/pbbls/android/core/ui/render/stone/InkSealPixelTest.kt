package app.pbbls.android.core.ui.render.stone

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.testing.PIXEL_TEST_SDK
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The ink seal prints in the colour it is given, keeping its print (#1307). A guard, not a proof of HWUI. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InkSealPixelTest {
    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun Seal(
        print: InkPrint,
        tag: String,
        outline: ValencePolarity = print.polarity,
    ) {
        val seal = rememberInkSealPrint(ValenceSizeGroup.MEDIUM, print, Color.Black, Color.White, outline = outline)
        Canvas(Modifier.size(60.dp).background(Color.White).testTag(tag)) { drawSeal(seal, size.width) }
    }

    private fun pixels(tag: String): PixelMap = compose.onNodeWithTag(tag).captureToImage().toPixelMap()

    private fun PixelMap.dark(): Int {
        var n = 0
        for (y in 0 until height) for (x in 0 until width) if (this[x, y].red < 0.5f) n++
        return n
    }

    @Test
    fun `relief fills in ink, blank keeps the paper inside its rim, dotted inks less than blank`() {
        compose.setContent {
            PebblesTheme {
                Row {
                    Seal(InkPrint.RELIEF, "relief")
                    Seal(InkPrint.BLANK, "blank")
                    Seal(InkPrint.DOTTED, "dotted")
                }
            }
        }
        val relief = pixels("relief")
        val blank = pixels("blank")
        val dotted = pixels("dotted")
        val cx = relief.width / 2
        val cy = relief.height / 3
        assertTrue("relief centre should be ink", relief[cx, cy].red < 0.2f)
        assertTrue("blank centre should be paper", blank[cx, cy].red > 0.9f)
        assertTrue("blank should ink its rim", blank.dark() > 0)
        assertTrue("dotted (${dotted.dark()}) should ink less than blank (${blank.dark()})", dotted.dark() < blank.dark())
    }

    @Test
    fun `a solid print cut on the neutral outline is a different stone from the highlight one`() {
        compose.setContent {
            PebblesTheme {
                Row {
                    Seal(InkPrint.RELIEF, "own")
                    Seal(InkPrint.RELIEF, "neutral", outline = ValencePolarity.NEUTRAL)
                }
            }
        }
        val own = pixels("own")
        val neutral = pixels("neutral")
        val cx = own.width / 2
        val cy = own.height / 3
        assertTrue("both solid at their centre", own[cx, cy].red < 0.2f && neutral[cx, cy].red < 0.2f)
        assertTrue("the outlines differ (${own.dark()} vs ${neutral.dark()})", own.dark() != neutral.dark())
    }
}

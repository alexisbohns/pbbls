package app.pbbls.android.features.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.home.components.HomeGreetingBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The greeting holds one line on a 360 dp phone up to 1.3 × font scale (D18), shrinking to fit,
 * and wraps at full size above it. Real text metrics (NATIVE graphics), real Ysabeau.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeGreetingFitTest {
    @get:Rule
    val compose = createComposeRule()

    private val lines =
        HomeGreeting.Lines(
            title = "Bonsoir, Alexandrine",
            line = "Qu'est-ce qui te reste de la journée ?",
            dateline = "samedi 31 octobre",
        )

    private fun layOut(fontScale: Float): TextLayoutResult {
        var layout: TextLayoutResult? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PebblesTheme {
                    // A 360 dp phone's column: 16 dp gutters and the greeting's 4 dp inset on each side.
                    Box(Modifier.width(PHONE - (GUTTER + INSET) * 2)) {
                        HomeGreetingBlock(lines = lines, onGreetingLayout = { layout = it })
                    }
                }
            }
        }
        compose.waitForIdle()
        return checkNotNull(layout)
    }

    @Test
    fun `a long french greeting holds one line at 1x`() {
        val layout = layOut(fontScale = 1f)
        assertEquals(1, layout.lineCount)
        assertFalse("the greeting is cut", layout.hasVisualOverflow)
    }

    @Test
    fun `it still holds one line at 1_3x`() {
        val layout = layOut(fontScale = 1.3f)
        assertEquals(1, layout.lineCount)
        assertFalse("the greeting is cut", layout.hasVisualOverflow)
    }

    @Test
    fun `above 1_3x it wraps at full size instead of shrinking`() {
        val layout = layOut(fontScale = 2f)
        assertTrue("expected two lines, got ${layout.lineCount}", layout.lineCount == 2)
        assertEquals(40f, layout.layoutInput.style.fontSize.value, 0.01f)
    }

    private companion object {
        val PHONE = 360.dp
        val GUTTER = 16.dp
        val INSET = 4.dp
    }
}

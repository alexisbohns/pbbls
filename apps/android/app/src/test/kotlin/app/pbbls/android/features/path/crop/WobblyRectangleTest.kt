package app.pbbls.android.features.path.crop

import android.app.Application
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import app.pbbls.android.core.common.SeededGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Ported from iOS `WobblyRectangleTests` (#1659). Robolectric for the platform `Path` under Compose's. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WobblyRectangleTest {
    private fun bounds(
        seed: String,
        size: Size,
        radius: Float = 0f,
    ): Rect = WobblyRectangle(seed).path(size, radius).getBounds()

    @Test
    fun `the same seed draws the same outline, another seed does not`() {
        val size = Size(40f, 50f)
        assertEquals(bounds("4:5", size), bounds("4:5", size))
        assertNotEquals(bounds("4:5", size), bounds("3:4", size))
    }

    @Test
    fun `the wobble stays within 5 percent of the rect`() {
        val b = bounds("1:1", Size(100f, 100f))
        assertTrue("$b", b.left in -5f..5f && b.top in -5f..5f && b.right in 95f..105f && b.bottom in 95f..105f)
    }

    @Test
    fun `rounded corners stay inside the rect`() {
        val b = bounds("3:4", Size(34f, 46f), radius = 14f)
        assertTrue("$b", b.left >= -2f && b.top >= -2f && b.right <= 36f && b.bottom <= 48f)
    }

    @Test
    fun `a radius past half the short side is clamped`() {
        val size = Size(34f, 46f)
        assertEquals(bounds("3:4", size, radius = 400f), bounds("3:4", size, radius = 17f))
    }

    @Test
    fun `the generator repeats for a seed and stays in -1 to 1`() {
        val a = SeededGenerator("tick-12")
        val b = SeededGenerator("tick-12")
        assertEquals(a.next(), b.next())
        assertEquals(a.unit(), b.unit(), 0.0)
        val c = SeededGenerator("angle-tick-0")
        repeat(1000) { assertTrue(c.unit() in -1.0..1.0) }
    }
}

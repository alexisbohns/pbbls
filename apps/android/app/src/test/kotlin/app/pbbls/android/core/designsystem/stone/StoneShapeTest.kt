package app.pbbls.android.core.designsystem.stone

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.pbbls.android.testing.PIXEL_TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The stone as a Compose [androidx.compose.ui.graphics.Shape], and the split
 * stone's cells. Native graphics, so the outline is a real `android.graphics.Path`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoneShapeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the outline fills its size`() {
        for (density in listOf(1f, 2.5f)) {
            val size = Size(100f * density, 60f * density)
            val outline = StoneShape("chip-0").createOutline(size, LayoutDirection.Ltr, Density(density))
            assertTrue(outline is Outline.Generic)
            val bounds = outline.bounds
            // The spline runs through points resampled off the rock, so it may miss its extremes by a hair.
            val tolerance = 1f * density
            assertEquals(0f, bounds.left, tolerance)
            assertEquals(0f, bounds.top, tolerance)
            assertEquals(size.width, bounds.right, tolerance)
            assertEquals(size.height, bounds.bottom, tolerance)
        }
    }

    @Test
    fun `two shapes with one seed are equal`() {
        assertEquals(StoneShape("settings", 16.dp), StoneShape("settings", 16.dp))
        assertEquals(StoneShape("settings", 16.dp).hashCode(), StoneShape("settings", 16.dp).hashCode())
        assertNotEquals(StoneShape("settings", 16.dp), StoneShape("chip-0", 16.dp))
    }

    @Test
    fun `split stone cells sit in their row cells`() {
        compose.setContent {
            Box(Modifier.width(359.dp).testTag(ROW)) {
                SplitStone(n = 3, seed = StoneGeometry.QUICK_ACTIONS_SEED, fills = List(3) { Color.Gray }, minHeight = 98.dp) { k ->
                    Box(Modifier.testTag("cell$k"))
                }
            }
        }
        val row = compose.onNodeWithTag(ROW).getBoundsInRoot()
        assertEquals(98f, (row.bottom - row.top).value, 0.5f)
        StoneGeometry.rowCells(Size(359f, 98f)).forEachIndexed { k, cell ->
            val bounds = compose.onNodeWithTag("cell$k", useUnmergedTree = true).getBoundsInRoot()
            assertEquals(cell.left, (bounds.left - row.left).value, 0.5f)
            assertEquals(cell.width, (bounds.right - bounds.left).value, 0.5f)
            assertEquals(98f, (bounds.bottom - bounds.top).value, 0.5f)
        }
    }

    @Test
    fun `a tall cell grows the whole row`() {
        compose.setContent {
            Box(Modifier.width(359.dp).testTag(ROW)) {
                SplitStone(n = 3, seed = StoneGeometry.QUICK_ACTIONS_SEED, fills = List(3) { Color.Gray }, minHeight = 98.dp) { k ->
                    Box(Modifier.height(if (k == 1) 140.dp else 20.dp).testTag("cell$k"))
                }
            }
        }
        assertEquals(140f, compose.onNodeWithTag(ROW).getBoundsInRoot().let { (it.bottom - it.top).value }, 0.5f)
        for (k in 0 until 3) {
            val bounds = compose.onNodeWithTag("cell$k", useUnmergedTree = true).getBoundsInRoot()
            assertEquals(140f, (bounds.bottom - bounds.top).value, 0.5f)
        }
    }

    @Test
    fun `vertical stacks full width stones a gutter apart`() {
        compose.setContent {
            Box(Modifier.width(300.dp).testTag(ROW)) {
                SplitStone(n = 3, seed = "days|pebbles|karma", fills = List(3) { Color.Gray }, minHeight = 60.dp, vertical = true) { k ->
                    Box(Modifier.testTag("cell$k"))
                }
            }
        }
        val row = compose.onNodeWithTag(ROW).getBoundsInRoot()
        assertEquals(3 * 60f + 2 * 8f, (row.bottom - row.top).value, 0.5f)
        for (k in 0 until 3) {
            val bounds = compose.onNodeWithTag("cell$k", useUnmergedTree = true).getBoundsInRoot()
            assertEquals(300f, (bounds.right - bounds.left).value, 0.5f)
            assertEquals(k * 68f, (bounds.top - row.top).value, 0.5f)
        }
    }

    private companion object {
        const val ROW = "row"
    }
}

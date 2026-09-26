package app.pbbls.android.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.ui.geometry.Rect
import androidx.window.core.layout.WindowSizeClass
import org.junit.Assert.assertEquals
import org.junit.Test

/** When the window gets two panes (#940). */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class PebblesPaneDirectiveTest {
    private val bookHinge =
        HingeInfo(
            bounds = Rect(left = 419f, top = 0f, right = 421f, bottom = 900f),
            isFlat = false,
            isVertical = true,
            isSeparating = true,
            isOccluding = false,
        )

    private fun info(
        minWidthDp: Int,
        hinges: List<HingeInfo> = emptyList(),
        isTabletop: Boolean = false,
    ) = WindowAdaptiveInfo(
        windowSizeClass = WindowSizeClass(minWidthDp = minWidthDp, minHeightDp = 480),
        windowPosture = Posture(isTabletop = isTabletop, hingeList = hinges),
    )

    @Test
    fun `a phone gets one pane`() {
        assertEquals(1, pebblesPaneDirective(info(minWidthDp = 0)).maxHorizontalPartitions)
    }

    @Test
    fun `a flat medium window gets one pane, as M3 recommends`() {
        assertEquals(1, pebblesPaneDirective(info(minWidthDp = 600)).maxHorizontalPartitions)
    }

    @Test
    fun `an expanded window gets two panes`() {
        assertEquals(2, pebblesPaneDirective(info(minWidthDp = 840)).maxHorizontalPartitions)
    }

    @Test
    fun `book posture splits a medium window at the hinge`() {
        val directive = pebblesPaneDirective(info(minWidthDp = 600, hinges = listOf(bookHinge)))

        assertEquals(2, directive.maxHorizontalPartitions)
        assertEquals(listOf(bookHinge.bounds), directive.excludedBounds)
    }

    @Test
    fun `a flat unfolded hinge does not force a split`() {
        val flat =
            HingeInfo(
                bounds = bookHinge.bounds,
                isFlat = true,
                isVertical = true,
                isSeparating = false,
                isOccluding = false,
            )
        assertEquals(1, pebblesPaneDirective(info(minWidthDp = 600, hinges = listOf(flat))).maxHorizontalPartitions)
    }

    @Test
    fun `tabletop posture does not force a side-by-side split`() {
        val tabletopHinge =
            HingeInfo(
                bounds = Rect(left = 0f, top = 449f, right = 700f, bottom = 451f),
                isFlat = false,
                isVertical = false,
                isSeparating = true,
                isOccluding = false,
            )
        val directive =
            pebblesPaneDirective(info(minWidthDp = 600, hinges = listOf(tabletopHinge), isTabletop = true))
        assertEquals(1, directive.maxHorizontalPartitions)
    }
}

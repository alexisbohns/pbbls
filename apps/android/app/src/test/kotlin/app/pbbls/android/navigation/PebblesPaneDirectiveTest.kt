package app.pbbls.android.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        minHeightDp: Int = 480,
    ) = WindowAdaptiveInfo(
        windowSizeClass = WindowSizeClass(minWidthDp = minWidthDp, minHeightDp = minHeightDp),
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
    fun `an extra-large window keeps at least two panes`() {
        // M3 gives three partitions from 1200 dp; list-detail only ever fills
        // two of them, so all that matters here is that it is not one.
        val directive = pebblesPaneDirective(info(minWidthDp = 1200))

        assertEquals(3, directive.maxHorizontalPartitions)
        assertTrue(directive.maxHorizontalPartitions >= 2)
    }

    @Test
    fun `book posture on a tall window stays one pane high`() {
        // M3 stacks two panes vertically in a single-column window that is
        // Expanded in height. Once the fold splits it side by side, that
        // stacking must go, as calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
        // does for a Medium width.
        val directive = pebblesPaneDirective(info(minWidthDp = 600, hinges = listOf(bookHinge), minHeightDp = 900))

        assertEquals(2, directive.maxHorizontalPartitions)
        assertEquals(1, directive.maxVerticalPartitions)
        assertEquals(0.dp, directive.verticalPartitionSpacerSize)
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

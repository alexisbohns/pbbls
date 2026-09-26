package app.pbbls.android.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.ui.unit.dp

/**
 * How many panes the window gets (#940): M3's recommendation, plus one rule
 * for foldables.
 *
 * `calculatePaneScaffoldDirective` gives Compact and Medium windows a single
 * pane whatever the posture. A foldable in book posture is usually Medium, so
 * the default would lay one pane straight across the fold. A separating
 * vertical hinge therefore raises the count to two; the hinge itself is
 * already in `excludedBounds` (the default `HingePolicy.AvoidSeparating`), so
 * the scaffold puts one pane on each side of it.
 *
 * Tabletop is deliberately left alone: its hinge is horizontal and list-detail
 * splits side by side, so a second partition would not move anything off it.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun pebblesPaneDirective(info: WindowAdaptiveInfo): PaneScaffoldDirective {
    val recommended = calculatePaneScaffoldDirective(info)
    val isBookPosture = info.windowPosture.hingeList.any { it.isVertical && it.isSeparating }
    return if (isBookPosture && recommended.maxHorizontalPartitions < 2) {
        recommended.copy(maxHorizontalPartitions = 2, horizontalPartitionSpacerSize = 24.dp)
    } else {
        recommended
    }
}

package app.pbbls.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass
import app.pbbls.android.navigation.PebblesKey
import app.pbbls.android.navigation.PebblesNavigationItems
import app.pbbls.android.navigation.pebblesNavigationSuiteColors
import app.pbbls.android.navigation.pebblesPaneDirective

/**
 * The directive production computes for a flat 840 dp window, rather than a
 * restated copy of it: two partitions, 24 dp apart.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private val expandedDirective =
    pebblesPaneDirective(WindowAdaptiveInfo(WindowSizeClass(minWidthDp = 840, minHeightDp = 720), Posture()))

/**
 * A list-detail pair as the Scene lays it out on a large screen (#940): the
 * collapsed rail, then the list and detail panes of a two-partition
 * `ListDetailPaneScaffold`. The directive and suite type are pinned rather
 * than read from the window, so each render is the layout it is named for.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ListDetailPreviewFrame(
    tab: PebblesKey,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    NavigationSuiteScaffold(
        navigationItems = {
            PebblesNavigationItems(
                current = tab,
                navigationSuiteType = NavigationSuiteType.WideNavigationRailCollapsed,
                onSelect = {},
                onReselect = {},
            )
        },
        navigationSuiteType = NavigationSuiteType.WideNavigationRailCollapsed,
        navigationSuiteColors = pebblesNavigationSuiteColors(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        ListDetailPaneScaffold(
            directive = expandedDirective,
            value =
                ThreePaneScaffoldValue(
                    primary = PaneAdaptedValue.Expanded,
                    secondary = PaneAdaptedValue.Expanded,
                    tertiary = PaneAdaptedValue.Hidden,
                ),
            listPane = { AnimatedPane { list() } },
            detailPane = { AnimatedPane { detail() } },
        )
    }
}

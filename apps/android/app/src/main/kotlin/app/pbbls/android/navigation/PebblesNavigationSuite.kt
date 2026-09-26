package app.pbbls.android.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationItemColors
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItemDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.pbbls.android.R

/**
 * Which navigation component the window gets (#855): the M3 adaptive
 * default, read once per window change. Compact width (every phone, since
 * phones are portrait-locked) is the short bottom bar; Medium and Expanded
 * are the collapsed wide rail on the start edge, except a tabletop posture
 * or a compact-height window, which keep a bar with start-aligned items.
 */
@Composable
fun pebblesNavigationSuiteType(): NavigationSuiteType = NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfo())

/**
 * The containers sit on `surface` like the screen, whichever component the
 * window gets (#853), so the bar and the rail read as the page's own edge.
 */
@Composable
fun pebblesNavigationSuiteColors(): NavigationSuiteColors {
    val surface = MaterialTheme.colorScheme.surface
    return NavigationSuiteDefaults.colors(
        shortNavigationBarContainerColor = surface,
        shortNavigationBarContentColor = MaterialTheme.colorScheme.onSurface,
        wideNavigationRailColors = WideNavigationRailDefaults.colors(containerColor = surface),
    )
}

/**
 * The four top-level destinations (#852, D2), as [NavigationSuiteItem]s for
 * the `NavigationSuiteScaffold` in `RootScreen` (#855). The same four items
 * render as a bottom bar on phones and a rail on large screens.
 *
 * Tapping a tab you are not on switches to it, retaining that tab's stack;
 * tapping the one you ARE on pops it to its root, which is the standard M3
 * escape hatch and the only reliable way out of a deep stack.
 *
 * Colors are the #853 branding on both components: the selected tab is
 * `primary` on a `primaryContainer` indicator, the rest `onSurfaceVariant`.
 * `Icon`/`Text` read their color from `LocalContentColor`, which the item
 * sets from these colors per selection state, so neither needs its own tint.
 */
@Composable
fun PebblesNavigationItems(
    current: PebblesKey,
    navigationSuiteType: NavigationSuiteType,
    onSelect: (PebblesKey) -> Unit,
    onReselect: (PebblesKey) -> Unit,
) {
    val colors = pebblesNavigationItemColors(navigationSuiteType)
    PebblesKey.tabs.forEach { tab ->
        val selected = tab == current
        NavigationSuiteItem(
            selected = selected,
            onClick = { if (selected) onReselect(tab) else onSelect(tab) },
            icon = { Icon(painter = painterResource(tab.iconRes()), contentDescription = null) },
            label = { NavigationLabel(tab) },
            navigationSuiteType = navigationSuiteType,
            colors = colors,
        )
    }
}

/**
 * One line, ellipsized: at the 2x accessibility font scale a quarter of a
 * phone's width no longer holds "Collections", and wrapping broke it mid-word
 * across two lines. The semantics keep the whole string, so TalkBack still
 * reads the full label.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NavigationLabel(tab: PebblesKey) {
    Text(
        text = stringResource(tab.labelRes()),
        style = MaterialTheme.typography.labelMediumEmphasized,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Bar items and rail items take the same [NavigationItemColors] type but
 * their own defaults (the rail's disabled and start-position roles differ),
 * so the branded roles are applied over the matching component's defaults.
 */
@Composable
private fun pebblesNavigationItemColors(type: NavigationSuiteType): NavigationItemColors {
    val colors = MaterialTheme.colorScheme
    return when (type) {
        NavigationSuiteType.WideNavigationRailCollapsed,
        NavigationSuiteType.WideNavigationRailExpanded,
        ->
            WideNavigationRailItemDefaults.colors(
                // The icon sits on the indicator, so it pairs with it; the
                // label sits beside or below it, on the container.
                selectedIconColor = colors.onPrimaryContainer,
                selectedTextColorTopIconPosition = colors.primary,
                selectedTextColorStartIconPosition = colors.primary,
                selectedIndicatorColor = colors.primaryContainer,
                unselectedIconColor = colors.onSurfaceVariant,
                unselectedTextColor = colors.onSurfaceVariant,
            )
        else ->
            ShortNavigationBarItemDefaults.colors(
                selectedIconColor = colors.onPrimaryContainer,
                selectedTextColorTopIconPosition = colors.primary,
                selectedTextColorStartIconPosition = colors.primary,
                selectedIndicatorColor = colors.primaryContainer,
                unselectedIconColor = colors.onSurfaceVariant,
                unselectedTextColor = colors.onSurfaceVariant,
            )
    }
}

@DrawableRes
private fun PebblesKey.iconRes(): Int =
    when (this) {
        PebblesKey.Path -> R.drawable.ic_stack
        PebblesKey.People -> R.drawable.ic_people
        PebblesKey.Collections -> R.drawable.ic_pebble_collection
        PebblesKey.You -> R.drawable.ic_person
        else -> error("$this is not a tab")
    }

@StringRes
private fun PebblesKey.labelRes(): Int =
    when (this) {
        PebblesKey.Path -> R.string.tab_path
        PebblesKey.People -> R.string.tab_people
        PebblesKey.Collections -> R.string.tab_collections
        PebblesKey.You -> R.string.tab_you
        else -> error("$this is not a tab")
    }

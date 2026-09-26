package app.pbbls.android.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.pbbls.android.R

/**
 * The four top-level destinations (#852, D2).
 *
 * Tapping a tab you are not on switches to it, retaining that tab's stack;
 * tapping the one you ARE on pops it to its root, which is the standard M3
 * escape hatch and the only reliable way out of a deep stack.
 *
 * `containerColor` and every `NavigationBarItemDefaults.colors(...)` role below
 * is set explicitly (#853): the bar sits on `surface` like the screen, the
 * selected tab is `primary` on a `primaryContainer` indicator, the rest
 * `onSurfaceVariant`. `Icon`/`Text` read their color from `LocalContentColor`, which
 * `NavigationBarItem` sets from these `colors` per selection state, so the
 * icon and label need no explicit `tint`/`color` of their own.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PebblesNavigationBar(
    current: PebblesKey,
    onSelect: (PebblesKey) -> Unit,
    onReselect: (PebblesKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    NavigationBar(
        modifier = modifier,
        containerColor = colors.surface,
        contentColor = colors.onSurface,
    ) {
        PebblesKey.tabs.forEach { tab ->
            val selected = tab == current
            NavigationBarItem(
                selected = selected,
                onClick = { if (selected) onReselect(tab) else onSelect(tab) },
                icon = { Icon(painter = painterResource(tab.iconRes()), contentDescription = null) },
                label = {
                    Text(
                        text = stringResource(tab.labelRes()),
                        style = MaterialTheme.typography.labelMediumEmphasized,
                    )
                },
                colors =
                    NavigationBarItemDefaults.colors(
                        // The icon sits on the indicator, so it pairs with it;
                        // the label sits below it, on the bar.
                        selectedIconColor = colors.onPrimaryContainer,
                        selectedTextColor = colors.primary,
                        indicatorColor = colors.primaryContainer,
                        unselectedIconColor = colors.onSurfaceVariant,
                        unselectedTextColor = colors.onSurfaceVariant,
                    ),
            )
        }
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

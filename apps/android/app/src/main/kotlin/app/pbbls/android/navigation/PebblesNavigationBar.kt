package app.pbbls.android.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.pbbls.android.R
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * The four top-level destinations (#852, D2).
 *
 * Tapping a tab you are not on switches to it, retaining that tab's stack;
 * tapping the one you ARE on pops it to its root, which is the standard M3
 * escape hatch and the only reliable way out of a deep stack.
 *
 * Material 3 is the rendering engine only here (M38 D6): `containerColor` and
 * every `NavigationBarItemDefaults.colors(...)` role below is built from
 * `PebblesTheme` tokens rather than left at Material's defaults, which would
 * otherwise paint the bar in Material's own palette instead of the app's.
 * `Icon`/`PebblesText` read their color from `LocalContentColor`, which
 * `NavigationBarItem` sets from these `colors` per selection state, so the
 * icon and label need no explicit `tint`/`color` of their own.
 */
@Composable
fun PebblesNavigationBar(
    current: PebblesKey,
    onSelect: (PebblesKey) -> Unit,
    onReselect: (PebblesKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    NavigationBar(
        modifier = modifier,
        containerColor = system.background,
        contentColor = system.foreground,
    ) {
        PebblesKey.tabs.forEach { tab ->
            val selected = tab == current
            NavigationBarItem(
                selected = selected,
                onClick = { if (selected) onReselect(tab) else onSelect(tab) },
                icon = { Icon(painter = painterResource(tab.iconRes()), contentDescription = null) },
                label = {
                    PebblesText(
                        text = stringResource(tab.labelRes()),
                        style = PebblesTypography.captionEmphasized,
                    )
                },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = accent.primary,
                        selectedTextColor = accent.primary,
                        indicatorColor = accent.surface,
                        unselectedIconColor = system.secondary,
                        unselectedTextColor = system.secondary,
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

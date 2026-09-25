package app.pbbls.android.features.glyph.store

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.Spacing

/** The three store tabs — ports iOS `GlyphTab` (rawValues mine/owned/commu). */
enum class GlyphTab(
    val labelRes: Int,
    val iconRes: Int,
) {
    MINE(R.string.glyph_tab_mine, R.drawable.ic_person),
    OWNED(R.string.glyph_tab_owned, R.drawable.ic_check_circle),
    COMMU(R.string.glyph_tab_commu, R.drawable.ic_person_pair),
}

/**
 * The store's three tabs as an M3 Expressive floating toolbar of toggle
 * buttons, floating over the grid's bottom edge — the iOS `GlyphTabBar` capsule
 * mapped onto the stock component (#854). Not a `NavigationBar`: the store
 * already sits inside the app's four-tab bar, and the same control lives in the
 * glyph picker sheet. The toggles form a `selectableGroup` with the radio
 * role, so TalkBack reads "Owned, selected, 2 of 3".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GlyphTabBar(
    selection: GlyphTab,
    onSelect: (GlyphTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier.padding(bottom = FloatingToolbarDefaults.ScreenOffset),
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
    ) {
        Row(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            GlyphTab.entries.forEach { tab ->
                ToggleButton(
                    checked = tab == selection,
                    onCheckedChange = { onSelect(tab) },
                    modifier = Modifier.semantics { role = Role.RadioButton },
                ) {
                    Icon(
                        painter = painterResource(tab.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(ToggleButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                    Text(text = stringResource(tab.labelRes))
                }
            }
        }
    }
}

package app.pbbls.android.features.glyph.store

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

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
 * Floating capsule tab pill pinned over the grid's bottom edge — ports iOS
 * `GlyphTabBar` (icon-over-caption segments, selected = accent on
 * accent-surface capsule; the "liquid glass" chrome maps to a shadowed
 * system-background capsule).
 */
@Composable
fun GlyphTabBar(
    selection: GlyphTab,
    onSelect: (GlyphTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    Row(
        modifier =
            modifier
                .widthIn(max = 320.dp)
                .padding(bottom = 10.dp)
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(50))
                .clip(RoundedCornerShape(50))
                .background(system.background)
                .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        GlyphTab.entries.forEach { tab ->
            val isSelected = tab == selection
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .then(if (isSelected) Modifier.background(accent.surface) else Modifier)
                        .clickable { onSelect(tab) }
                        .padding(vertical = 10.dp)
                        .semantics { selected = isSelected },
            ) {
                Icon(
                    painter = painterResource(tab.iconRes),
                    contentDescription = null,
                    tint = if (isSelected) accent.primary else system.secondary,
                    modifier = Modifier.size(15.dp),
                )
                PebblesText(
                    text = stringResource(tab.labelRes),
                    style = PebblesTypography.captionEmphasized,
                    color = if (isSelected) accent.primary else system.secondary,
                )
            }
        }
    }
}

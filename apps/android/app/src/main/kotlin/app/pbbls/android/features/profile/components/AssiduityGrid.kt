package app.pbbls.android.features.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.R

/**
 * 7-column grid of the last 28 days — ports iOS `AssiduityGrid.swift`: active
 * days render the fossil-shell glyph in `primary`, inactive days the wave
 * glyph in faint `outlineVariant`. One combined a11y label; the cells are decorative.
 */
@Composable
fun AssiduityGrid(
    data: List<Boolean>,
    modifier: Modifier = Modifier,
    columns: Int = 7,
    cellSize: Dp = 7.dp,
    cellSpacing: Dp = 4.dp,
) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.profile_assiduity_a11y)
    Column(
        verticalArrangement = Arrangement.spacedBy(cellSpacing),
        modifier = modifier.clearAndSetSemantics { contentDescription = label },
    ) {
        chunkAssiduity(data, columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(cellSpacing)) {
                row.forEach { active ->
                    Icon(
                        painter =
                            painterResource(
                                if (active) R.drawable.ic_fossil_shell else R.drawable.ic_alternating_current,
                            ),
                        contentDescription = null,
                        tint = if (active) colors.primary else colors.outlineVariant,
                        modifier = Modifier.size(cellSize),
                    )
                }
            }
        }
    }
}

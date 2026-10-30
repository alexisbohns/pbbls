package app.pbbls.android.features.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.R

/**
 * 7-column grid of the last 28 days — ports iOS `AssiduityGrid.swift`. A 7dp
 * cell is too small for any icon (Android icon migration spec D4), so an
 * active day is a filled `primary` dot and a day without a pebble a 1dp
 * `surfaceContainerHighest` ring, iOS's two shapes. One combined a11y label;
 * the cells are decorative.
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
                    Box(
                        modifier =
                            if (active) {
                                Modifier.size(cellSize).background(colors.primary, CircleShape)
                            } else {
                                Modifier.size(cellSize).border(1.dp, colors.surfaceContainerHighest, CircleShape)
                            },
                    )
                }
            }
        }
    }
}

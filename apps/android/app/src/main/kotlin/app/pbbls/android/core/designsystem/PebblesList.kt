package app.pbbls.android.core.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

/**
 * Where a row sits inside its section, used to pick which corners of the
 * 1dp `outlineVariant` border round. `ONLY` is the default for single-row
 * sections. Mirrors iOS `Theme/PebblesList.swift`.
 */
enum class PebblesListRowPosition {
    ONLY,
    TOP,
    MIDDLE,
    BOTTOM,
}

/** Pure position rule — ports `pebblesRowPosition(index:count:)` (JVM-tested). */
fun pebblesRowPosition(
    index: Int,
    count: Int,
): PebblesListRowPosition =
    when {
        count <= 1 -> PebblesListRowPosition.ONLY
        index == 0 -> PebblesListRowPosition.TOP
        index == count - 1 -> PebblesListRowPosition.BOTTOM
        else -> PebblesListRowPosition.MIDDLE
    }

/**
 * Row chrome — the `pebblesListRow(position:)` analog: a 1dp `outlineVariant`
 * border whose `shapes.large` corners round only the edges this row owns.
 * [PebblesListSection] overlaps adjacent rows by the border width so the
 * shared edge renders as a single divider, completing the bordered card.
 */
fun Modifier.pebblesListRow(position: PebblesListRowPosition = PebblesListRowPosition.ONLY): Modifier =
    composed {
        val large = MaterialTheme.shapes.large
        val shape =
            when (position) {
                PebblesListRowPosition.ONLY -> large
                PebblesListRowPosition.TOP -> large.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
                PebblesListRowPosition.MIDDLE -> RectangleShape
                PebblesListRowPosition.BOTTOM -> large.copy(topStart = ZeroCornerSize, topEnd = ZeroCornerSize)
            }
        border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    }

/**
 * A bordered section of rows — the `List`/`Form` + `pebblesListRow` idiom as
 * one composable, since Compose has no grouped-list container. Each row is
 * wrapped in the position-aware border and given the standard row insets;
 * rows overlap by 1dp so adjacent borders coincide into a single divider.
 *
 * [rows] is an explicit list (not a free content block) because the border
 * needs each row's index/count — the same reason the iOS API takes an
 * explicit `position:`.
 */
@Composable
fun PebblesListSection(
    rows: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    header: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (header != null) {
            PebblesSectionHeader(
                text = header,
                modifier = Modifier.padding(bottom = PebblesTheme.spacing.sm),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy((-1).dp)) {
            rows.forEachIndexed { index, row ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .pebblesListRow(pebblesRowPosition(index, rows.size))
                            .padding(horizontal = PebblesTheme.spacing.lg, vertical = PebblesTheme.spacing.md),
                ) {
                    row()
                }
            }
        }
    }
}

/**
 * Section header typography matching profile cards — the
 * `pebblesSectionHeader()` analog: `titleSmall` in `onSurfaceVariant`,
 * sentence case (#853 dropped the uppercase transform).
 */
@Composable
fun PebblesSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

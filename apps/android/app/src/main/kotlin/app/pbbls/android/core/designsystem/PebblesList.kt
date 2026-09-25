package app.pbbls.android.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Row insets for [PebblesListSection]. */
object PebblesListDefaults {
    /** For free-form rows (a pebble row, a picker): the section pads them. */
    val ContentRowPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

    /** For `ListItem` rows, which carry their own insets and must reach the card's edges to ripple there. */
    val ListItemRowPadding = PaddingValues(0.dp)
}

/**
 * A grouped section of rows — a stock `OutlinedCard` with a `HorizontalDivider`
 * between rows, under an optional [header] (#854; it was a hand-drawn,
 * position-aware border per row, the iOS `List`/`Form` idiom). [rows] stays an
 * explicit list so the dividers go between rows rather than around them.
 *
 * Settings-style rows (text, switch, chevron) should be `ListItem`s passed
 * with [PebblesListDefaults.ListItemRowPadding], so each is one full-width
 * target; free-form content keeps the default [rowPadding].
 */
@Composable
fun PebblesListSection(
    rows: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    header: String? = null,
    rowPadding: PaddingValues = PebblesListDefaults.ContentRowPadding,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (header != null) {
            PebblesSectionHeader(
                text = header,
                modifier = Modifier.padding(bottom = PebblesTheme.spacing.sm),
            )
        }
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(modifier = Modifier.fillMaxWidth().padding(rowPadding)) {
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

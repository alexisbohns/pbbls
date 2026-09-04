package app.pbbls.android.features.path.record.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.path.models.PebbleCollection
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import app.pbbls.android.theme.Spacing

/**
 * Step 7 — which collection, if any — ports iOS `RecordCollectionStep`.
 * Single-select, so a tap commits and advances; Skip is how the user says none
 * (M58 D3).
 *
 * No inline creation: collection creation lives in Profile, and adding a second
 * entry point here is out of scope for the flow.
 */
@Composable
fun RecordCollectionStep(
    collections: List<PebbleCollection>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent

    if (collections.isEmpty()) {
        PebblesText(
            text = stringResource(R.string.record_collection_empty),
            style = PebblesTypography.callout,
            color = system.secondary,
            textAlign = TextAlign.Center,
            modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xxl),
        )
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        collections.forEach { collection ->
            val isSelected = collection.id == selectedId
            val foreground = if (isSelected) accent.primary else system.foreground
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) accent.primary.copy(alpha = 0.12f) else system.muted)
                        .clickable { onSelect(collection.id) }
                        .padding(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_pebble_collection),
                    contentDescription = null,
                    tint = if (isSelected) accent.primary else system.secondary,
                    modifier = Modifier.size(24.dp),
                )
                // Collection names are user-authored, so never localized.
                PebblesText(
                    text = collection.name,
                    style = PebblesTypography.body,
                    color = foreground,
                    maxLines = 1,
                )
            }
        }
    }
}

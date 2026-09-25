package app.pbbls.android.features.path.record.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.model.PebbleCollection

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
    val colors = MaterialTheme.colorScheme

    if (collections.isEmpty()) {
        Text(
            text = stringResource(R.string.record_collection_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xxl),
        )
        return
    }

    // One radio group: TalkBack reads the name, "selected", and "2 of 5".
    Column(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        collections.forEach { collection ->
            val isSelected = collection.id == selectedId
            // A selected row is a primaryContainer fill, so its content reads the paired role.
            val foreground = if (isSelected) colors.onPrimaryContainer else colors.onSurface
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(if (isSelected) colors.primaryContainer else colors.surfaceContainerHighest)
                        .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(collection.id) }
                        .padding(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_pebble_collection),
                    contentDescription = null,
                    tint = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                // Collection names are user-authored, so never localized.
                Text(
                    text = collection.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = foreground,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                // onClick = null: the row owns the selection; the radio is its visible state.
                RadioButton(
                    selected = isSelected,
                    onClick = null,
                    colors =
                        RadioButtonDefaults.colors(
                            selectedColor = foreground,
                            unselectedColor = colors.onSurfaceVariant,
                        ),
                )
            }
        }
    }
}

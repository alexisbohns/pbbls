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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import app.pbbls.android.core.model.Visibility
import app.pbbls.android.core.ui.iconRes
import app.pbbls.android.core.ui.labelRes

/**
 * Step 9 — who gets to see it, and the publish button — ports iOS
 * `RecordPrivacyStep`.
 *
 * The grade is the decision most coupled to "am I ready for other people to see
 * this", which is why it sits against publish rather than in a bottom-bar chip
 * eight fields away (M58 D2).
 *
 * A tap selects and does not advance (D3). The snap state and any publish error
 * live here too, because this is where the user is standing when publishing is
 * blocked or fails (D10).
 */
@Composable
fun RecordPrivacyStep(
    selected: Visibility,
    onSelect: (Visibility) -> Unit,
    modifier: Modifier = Modifier,
    snapBlockedMessage: String? = null,
    publishError: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // One radio group: TalkBack reads "Private, selected, 2 of 3".
        Column(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Visibility.entries.forEach { grade ->
                GradeRow(grade = grade, isSelected = grade == selected, onSelect = { onSelect(grade) })
            }
        }

        if (snapBlockedMessage != null) {
            Text(
                text = snapBlockedMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            )
        }

        if (publishError != null) {
            Text(
                text = publishError,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GradeRow(
    grade: Visibility,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // A selected row is a primaryContainer fill, so its content reads the paired role.
    val foreground = if (isSelected) colors.onPrimaryContainer else colors.onSurface
    val secondaryForeground = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant
    val label = stringResource(grade.labelRes)
    val explanation = stringResource(grade.explanationRes)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(if (isSelected) colors.primaryContainer else colors.surfaceContainerHighest)
                .selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect)
                .padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(grade.iconRes),
            contentDescription = null,
            tint = secondaryForeground,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLargeEmphasized,
                color = foreground,
            )
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryForeground,
            )
        }
        // onClick = null: the row owns the selection; the radio is its visible state.
        RadioButton(
            selected = isSelected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = foreground, unselectedColor = secondaryForeground),
        )
    }
}

/**
 * One line per M51 grade. Deliberately not on `VisibilityUi` alongside
 * [labelRes]: the chip and the badge want the bare label, and only this step has
 * room for the explanation.
 */
private val Visibility.explanationRes: Int
    get() =
        when (this) {
            Visibility.SECRET -> R.string.record_privacy_secret_explanation
            Visibility.PRIVATE -> R.string.record_privacy_private_explanation
            Visibility.PUBLIC -> R.string.record_privacy_public_explanation
        }

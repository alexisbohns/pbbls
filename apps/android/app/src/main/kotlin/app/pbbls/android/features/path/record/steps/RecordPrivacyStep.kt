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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.path.models.Visibility
import app.pbbls.android.features.path.models.iconRes
import app.pbbls.android.features.path.models.labelRes
import app.pbbls.android.theme.PebblesDestructive
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import app.pbbls.android.theme.Spacing

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
    val system = PebblesTheme.colors.system
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Visibility.entries.forEach { grade ->
            GradeRow(grade = grade, isSelected = grade == selected, onSelect = { onSelect(grade) })
        }

        if (snapBlockedMessage != null) {
            PebblesText(
                text = snapBlockedMessage,
                style = PebblesTypography.subhead,
                color = system.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            )
        }

        if (publishError != null) {
            PebblesText(
                text = publishError,
                style = PebblesTypography.callout,
                color = PebblesDestructive,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            )
        }
    }
}

@Composable
private fun GradeRow(
    grade: Visibility,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    val label = stringResource(grade.labelRes)
    val explanation = stringResource(grade.explanationRes)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) accent.primary.copy(alpha = 0.12f) else system.muted)
                .clickable(onClick = onSelect)
                .padding(Spacing.md)
                .clearAndSetSemantics { contentDescription = "$label. $explanation" },
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(grade.iconRes),
            contentDescription = null,
            tint = if (isSelected) accent.primary else system.secondary,
            modifier = Modifier.size(24.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PebblesText(
                text = label,
                style = PebblesTypography.bodyEmphasized,
                color = if (isSelected) accent.primary else system.foreground,
            )
            PebblesText(
                text = explanation,
                style = PebblesTypography.subhead,
                color = system.secondary,
            )
        }
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

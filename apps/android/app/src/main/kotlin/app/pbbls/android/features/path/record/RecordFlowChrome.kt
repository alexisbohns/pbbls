package app.pbbls.android.features.path.record

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.Spacing

/**
 * The record flow's top bar: back chevron, progress dots, close — ports iOS
 * `RecordFlowChrome`.
 *
 * Minimal by design (M58 D2): picking is the advance, so there is no Next
 * button competing with the dots, and "Save as draft" lives in the close
 * confirmation rather than taking permanent residence here (D9).
 */
@Composable
fun RecordFlowChrome(
    step: RecordStep,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val canGoBack = step.previous != null

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Kept in the layout at zero alpha rather than removed, so the dots do
        // not shift sideways between step 0 and step 1.
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .alpha(if (canGoBack) 1f else 0f)
                    .clip(CircleShape)
                    .clickable(enabled = canGoBack, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = if (canGoBack) stringResource(R.string.record_back_a11y) else null,
                tint = system.secondary,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        ProgressDots(step = step)

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_x_circle),
                contentDescription = stringResource(R.string.action_close),
                tint = system.secondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * One element to TalkBack, not ten: "Step 4 of 10" is the useful reading, and
 * ten unlabeled dots is not.
 */
@Composable
private fun ProgressDots(
    step: RecordStep,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    val current = step.dotIndex
    val announcement =
        stringResource(R.string.record_step_a11y, (current ?: 0) + 1, RecordStep.counted.size)

    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = announcement },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecordStep.counted.forEach { candidate ->
            val index = candidate.dotIndex
            val filled = current != null && index != null && index <= current
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (filled) accent.primary else system.muted),
            )
        }
    }
}

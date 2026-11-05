package app.pbbls.android.features.onboarding.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.model.OnboardingReason

/** The tile of [reason], for the tests. */
fun onboardingReasonTag(reason: OnboardingReason): String = "onboarding_reason_${reason.slug}"

/**
 * Step 3 (#1510 D4): six equal tiles in two columns, multi-select; a selected tile is
 * `primaryContainer` with a lit stone in its corner.
 */
@Composable
fun ReasonsStep(
    selected: Set<OnboardingReason>,
    onToggle: (OnboardingReason) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            stringResource(R.string.onboarding_reasons_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.onboarding_reasons_body), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        OnboardingReason.entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                pair.forEach { reason ->
                    val on = reason in selected
                    val ink = if (on) colors.onPrimaryContainer else colors.onSurface
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1.15f)
                            .clip(MaterialTheme.shapes.large)
                            .background(if (on) colors.primaryContainer else colors.surfaceContainer)
                            .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle(reason) })
                            .padding(Spacing.lg)
                            .testTag(onboardingReasonTag(reason)),
                    ) {
                        if (on) {
                            PbblsIconImage(
                                PbblsIcon.PEBBLE_ROUND_LIT_1,
                                PbblsIconSize.ROW,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.align(Alignment.TopEnd),
                            )
                        }
                        Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            PbblsIconImage(reason.icon, PbblsIconSize.TILE, contentDescription = null, tint = ink)
                            Text(stringResource(reason.labelRes), style = MaterialTheme.typography.titleSmall, color = ink)
                        }
                    }
                }
            }
        }
        Spacer(
            Modifier
                .weight(1f)
                .height(Spacing.lg),
        )
        PebblesPrimaryButton(
            text = stringResource(R.string.onboarding_continue),
            onClick = onContinue,
            enabled = selected.isNotEmpty(),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(JourneyTags.ONBOARDING_NEXT),
        )
    }
}

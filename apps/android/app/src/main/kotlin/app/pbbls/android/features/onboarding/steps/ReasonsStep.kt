package app.pbbls.android.features.onboarding.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.designsystem.PageTypography
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
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            StepTitle(stringResource(R.string.onboarding_reasons_title))
            Text(stringResource(R.string.onboarding_reasons_body), style = PageTypography.lead, color = colors.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OnboardingReason.entries.chunked(2).forEach { pair ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    pair.forEach { reason ->
                        val on = reason in selected
                        val ink = if (on) colors.onPrimaryContainer else colors.onSurface
                        val shape = MaterialTheme.shapes.extraLarge
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = TILE_HEIGHT)
                                .clip(shape)
                                .background(if (on) colors.primaryContainer else colors.surfaceContainerLowest)
                                .then(if (on) Modifier else Modifier.border(1.dp, colors.outlineVariant, shape))
                                .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle(reason) })
                                .padding(Spacing.lg)
                                .testTag(onboardingReasonTag(reason)),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                PbblsIconImage(reason.icon, PbblsIconSize.TILE, contentDescription = null, tint = ink)
                                PbblsIconImage(
                                    if (on) PbblsIcon.PEBBLE_ROUND_LIT_1 else PbblsIcon.PEBBLE_ROUND,
                                    PbblsIconSize.ROW,
                                    contentDescription = null,
                                    tint = if (on) colors.primary else colors.outline,
                                )
                            }
                            Text(stringResource(reason.labelRes), style = MaterialTheme.typography.bodyMedium, color = ink)
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

private val TILE_HEIGHT = 112.dp

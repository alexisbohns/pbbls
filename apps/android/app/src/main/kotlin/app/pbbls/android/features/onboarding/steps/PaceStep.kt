package app.pbbls.android.features.onboarding.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.designsystem.PageTypography
import app.pbbls.android.core.designsystem.PbblsGlyphImage
import app.pbbls.android.core.designsystem.PbblsGlyphSize
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.model.OnboardingPace

/** The button of [pace], for the tests. */
fun onboardingPaceTag(pace: OnboardingPace): String = "onboarding_pace_${pace.slug}"

private val OUTER = 32.dp
private val INNER = 10.dp
private val ROW_HEIGHT = 88.dp
private val ICON_DISC = 52.dp

/**
 * The connected shape of a row in a VERTICAL group. `ButtonGroupDefaults.connected*ButtonShapes`
 * are for a horizontal group: they round the start and end, not the top and bottom.
 */
private fun verticalShape(
    index: Int,
    last: Int,
): Shape =
    when (index) {
        0 -> RoundedCornerShape(topStart = OUTER, topEnd = OUTER, bottomStart = INNER, bottomEnd = INNER)
        last -> RoundedCornerShape(topStart = INNER, topEnd = INNER, bottomStart = OUTER, bottomEnd = OUTER)
        else -> RoundedCornerShape(INNER)
    }

/**
 * Step 6 (#1510 D11, D12): the four paces as one vertical connected group, each with its icon and a
 * line under its name, the chosen one a filled pill. "No streaks, no guilt." sits above the button.
 * The button sends everything (`complete_onboarding`), so it loads while [isCompleting] and
 * [errorRes] is the failed send's line.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PaceStep(
    selected: OnboardingPace?,
    isCompleting: Boolean,
    errorRes: Int?,
    onSelect: (OnboardingPace) -> Unit,
    onFinish: () -> Unit,
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
        StepTitle(stringResource(R.string.onboarding_pace_title))
        Column(
            Modifier
                .fillMaxWidth()
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            val paces = OnboardingPace.entries
            paces.forEachIndexed { index, pace ->
                val isSelected = pace == selected
                val shape = verticalShape(index, paces.lastIndex)
                ToggleButton(
                    checked = isSelected,
                    onCheckedChange = { onSelect(pace) },
                    enabled = !isCompleting,
                    shapes = ToggleButtonShapes(shape = shape, pressedShape = shape, checkedShape = CircleShape),
                    colors =
                        ToggleButtonColors(
                            containerColor = colors.surfaceContainer,
                            contentColor = colors.onSurface,
                            disabledContainerColor = colors.surfaceContainer,
                            disabledContentColor = colors.onSurfaceVariant,
                            checkedContainerColor = colors.primary,
                            checkedContentColor = colors.onPrimary,
                        ),
                    contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = ROW_HEIGHT)
                            .semantics {
                                role = Role.RadioButton
                                this.selected = isSelected
                            }.testTag(onboardingPaceTag(pace)),
                ) {
                    Box(
                        Modifier
                            .size(ICON_DISC)
                            .clip(CircleShape)
                            .background(if (isSelected) colors.onPrimary.copy(alpha = DISC_ALPHA) else colors.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        PbblsGlyphImage(
                            pace.icon,
                            PbblsGlyphSize.TILE,
                            contentDescription = null,
                            tint = if (isSelected) colors.onPrimary else colors.onSurface,
                        )
                    }
                    Spacer(Modifier.width(Spacing.lg))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(pace.labelRes), style = PageTypography.wrapMonth)
                        Text(stringResource(pace.detailRes), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        errorRes?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = colors.error) }
        Spacer(
            Modifier
                .weight(1f)
                .height(Spacing.lg),
        )
        Text(
            stringResource(R.string.onboarding_pace_footer),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        PebblesPrimaryButton(
            text = stringResource(R.string.onboarding_finish),
            onClick = onFinish,
            enabled = !isCompleting,
            isLoading = isCompleting,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(JourneyTags.ONBOARDING_NEXT),
        )
    }
}

private const val DISC_ALPHA = 0.18f

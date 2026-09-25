package app.pbbls.android.features.path.record

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.rememberReduceMotion

/**
 * The record flow's top bar: back, progress, close — ports iOS
 * `RecordFlowChrome` onto a stock `CenterAlignedTopAppBar` (#854), with the
 * ten dots replaced by an Expressive `LinearWavyProgressIndicator`.
 *
 * Minimal by design (M58 D2): picking is the advance, so there is no Next
 * button competing with the progress, and "Save as draft" lives in the close
 * confirmation rather than taking permanent residence here (D9). The host
 * applies the safe-drawing insets, so the bar's own resolve to zero.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordFlowChrome(
    step: RecordStep,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canGoBack = step.previous != null
    CenterAlignedTopAppBar(
        title = { StepProgress(step = step) },
        modifier = modifier,
        navigationIcon = {
            // Kept in the layout at zero alpha rather than removed, so the
            // progress does not shift sideways between step 0 and step 1.
            IconButton(
                onClick = onBack,
                enabled = canGoBack,
                modifier = Modifier.alpha(if (canGoBack) 1f else 0f),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = if (canGoBack) stringResource(R.string.record_back_a11y) else null,
                )
            }
        },
        actions = {
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.ic_x_circle),
                    contentDescription = stringResource(R.string.action_close),
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
    )
}

/**
 * How far through the counted steps the flow is. One element to TalkBack:
 * "Step 4 of 10" is the useful reading, and a bare percentage is not. The fill
 * eases between steps unless the system has animations off.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StepProgress(
    step: RecordStep,
    modifier: Modifier = Modifier,
) {
    val position = (step.dotIndex ?: 0) + 1
    val total = RecordStep.counted.size
    val announcement = stringResource(R.string.record_step_a11y, position, total)
    val reduceMotion = rememberReduceMotion()
    val progress by animateFloatAsState(
        targetValue = position.toFloat() / total,
        animationSpec = if (reduceMotion) snap() else MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "recordStepProgress",
    )
    LinearWavyProgressIndicator(
        progress = { progress },
        modifier = modifier.width(ProgressWidth).clearAndSetSemantics { contentDescription = announcement },
    )
}

private val ProgressWidth = 160.dp

package app.pbbls.android.features.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.designsystem.readableWidth
import app.pbbls.android.core.model.ConsentKind
import app.pbbls.android.core.model.OnboardingReason
import app.pbbls.android.features.onboarding.steps.ConsentStep
import app.pbbls.android.features.onboarding.steps.ProgressStones
import app.pbbls.android.features.onboarding.steps.PromiseStep
import app.pbbls.android.features.onboarding.steps.ReasonsStep

/**
 * The onboarding entry (#1510): a top bar with Back, the progress stones and Skip or Later, and the
 * current step below. System back steps back inside the flow and is consumed on the first step: the
 * flow is left only by completing it (D1, D10). Pushed over Home by `RootScreen`; [onFinish] pops it.
 *
 * Back goes through [NavigationBackHandler], not the legacy `BackHandler`, so a completed gesture
 * never reaches `NavDisplay`'s own predictive-pop transition. It is skipped under preview and
 * screenshot rendering, where no `NavigationEventDispatcherOwner` is provided.
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isDone) { if (state.isDone) onFinish() }
    if (!LocalInspectionMode.current) {
        val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
        NavigationBackHandler(state = backState) { viewModel.back() }
    }
    OnboardingContent(
        state = state,
        onBack = viewModel::back,
        onSkip = viewModel::skip,
        onNext = viewModel::next,
        onConsentToggle = viewModel::onConsentToggle,
        onReasonToggle = viewModel::onReasonToggle,
        modifier = modifier,
    )
}

/** Stateless, what the screenshots render: the top bar and the step [state] names. */
@Composable
fun OnboardingContent(
    state: OnboardingUiState,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    onConsentToggle: (ConsentKind, Boolean) -> Unit,
    onReasonToggle: (OnboardingReason) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val steps = OnboardingSteps.visible
    Box(
        modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .readableWidth(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    enabled = state.stepIndex > 0,
                    modifier = Modifier.testTag(JourneyTags.ONBOARDING_BACK),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.onboarding_back),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    ProgressStones(count = steps.size, current = state.stepIndex)
                }
                when (state.step.skip) {
                    // The Back button's width, so the stones stay centred on a step with no skip.
                    OnboardingSkip.NONE -> Box(Modifier.size(48.dp))
                    OnboardingSkip.SKIP, OnboardingSkip.LATER ->
                        TextButton(onClick = onSkip, modifier = Modifier.testTag(JourneyTags.ONBOARDING_SKIP)) {
                            Text(
                                stringResource(
                                    if (state.step.skip ==
                                        OnboardingSkip.SKIP
                                    ) {
                                        R.string.onboarding_skip
                                    } else {
                                        R.string.onboarding_later
                                    },
                                ),
                            )
                        }
                }
            }
            AnimatedContent(
                targetState = state.step.id,
                transitionSpec = {
                    val forward = steps.indexOfFirst { it.id == targetState } >= steps.indexOfFirst { it.id == initialState }
                    val sign = if (forward) 1 else -1
                    (slideInHorizontally { sign * it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -sign * it / 4 } + fadeOut())
                },
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                label = "onboarding-step",
            ) { id ->
                when (id) {
                    "promise" -> PromiseStep(state.letter, state.letterUnavailable, onNext)
                    "consent" -> ConsentStep(state.consent, onConsentToggle, onNext)
                    "reasons" -> ReasonsStep(state.reasons, onReasonToggle, onNext)
                    else -> Box(Modifier.fillMaxSize()) // steps 4–6 land in parts 5 and 6
                }
            }
        }
    }
}

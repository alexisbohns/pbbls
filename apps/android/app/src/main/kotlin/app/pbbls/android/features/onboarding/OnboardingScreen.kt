package app.pbbls.android.features.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.Surface
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
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.OnboardingPace
import app.pbbls.android.core.model.OnboardingReason
import app.pbbls.android.core.ui.GlyphCarveSlot
import app.pbbls.android.features.onboarding.steps.ConsentStep
import app.pbbls.android.features.onboarding.steps.PaceStep
import app.pbbls.android.features.onboarding.steps.PeopleStep
import app.pbbls.android.features.onboarding.steps.ProgressStones
import app.pbbls.android.features.onboarding.steps.PromiseStep
import app.pbbls.android.features.onboarding.steps.ReasonsStep
import app.pbbls.android.features.onboarding.steps.SoulGlyphSheet
import app.pbbls.android.features.onboarding.steps.YouStep

/**
 * The onboarding entry (#1510): a top bar with Back, the progress stones and Skip or Later, and the
 * current step below. System back steps back inside the flow and is consumed on the first step: the
 * flow is left only by completing it (D1, D10). Pushed over Home by `RootScreen`; [onFinish] pops it.
 *
 * Back goes through [NavigationBackHandler], not the legacy `BackHandler`, so a completed gesture
 * never reaches `NavDisplay`'s own predictive-pop transition. It is skipped under preview and
 * screenshot rendering, where no `NavigationEventDispatcherOwner` is provided.
 *
 * [carve] is the glyph studio (S1, D8), a content swap over the whole shell while
 * [OnboardingUiState.carvingFor] is set, the way the glyph picker hosts it: a pushed entry would
 * have no way to hand the carved glyph back. Back while carving cancels the carve, not the step.
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    carve: GlyphCarveSlot,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isDone) { if (state.isDone) onFinish() }
    if (!LocalInspectionMode.current) {
        val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
        NavigationBackHandler(state = backState) {
            if (state.carvingFor != null) viewModel.onCarveCancelled() else viewModel.back()
        }
    }
    if (state.carvingFor != null) {
        carve(viewModel::onCarved, viewModel::onCarveCancelled, modifier)
        return
    }
    OnboardingContent(
        state = state,
        onBack = viewModel::back,
        onSkip = viewModel::skip,
        onNext = viewModel::next,
        onConsentToggle = viewModel::onConsentToggle,
        onReasonToggle = viewModel::onReasonToggle,
        onNameChange = viewModel::onNameChange,
        onGlyphSelect = { viewModel.onGlyphPick(it.id) },
        onDraw = viewModel::onDrawForYou,
        onPersonAdd = viewModel::onPersonAdd,
        onPersonNameChange = viewModel::onPersonNameChange,
        onPersonCommit = viewModel::onPersonCommit,
        onPersonRemove = viewModel::onPersonRemove,
        onPersonGlyphSheet = viewModel::onPersonGlyphSheet,
        onPersonGlyphPick = viewModel::onPersonGlyphPick,
        onDrawForPerson = viewModel::onDrawForPerson,
        onPaceSelect = viewModel::onPaceSelect,
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
    onNameChange: (String) -> Unit,
    onGlyphSelect: (Glyph) -> Unit,
    onDraw: () -> Unit,
    onPersonAdd: () -> Unit,
    onPersonNameChange: (Int, String) -> Unit,
    onPersonCommit: (Int) -> Unit,
    onPersonRemove: (Int) -> Unit,
    onPersonGlyphSheet: (Int?) -> Unit,
    onPersonGlyphPick: (Int, String) -> Unit,
    onDrawForPerson: (Int) -> Unit,
    onPaceSelect: (OnboardingPace) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val steps = OnboardingSteps.visible
    // A Surface, not a painted Box: it sets the content colour to `onSurface`, so the step
    // titles and the Back arrow (which take `LocalContentColor`) read in dark mode too.
    Surface(modifier.fillMaxSize(), color = colors.surface) {
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
                    enabled = state.stepIndex > 0 && !state.isCompleting,
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
                        TextButton(
                            onClick = onSkip,
                            enabled = !state.isCompleting,
                            modifier = Modifier.testTag(JourneyTags.ONBOARDING_SKIP),
                        ) {
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
                    "you" ->
                        YouStep(
                            state.name,
                            state.systemGlyphs + state.carved,
                            state.glyphId,
                            onNameChange,
                            onGlyphSelect,
                            onDraw,
                            onNext,
                        )
                    "people" ->
                        PeopleStep(
                            people = state.people,
                            glyphs = state.systemGlyphs + state.carved,
                            onAdd = onPersonAdd,
                            onNameChange = onPersonNameChange,
                            onCommit = onPersonCommit,
                            onRemove = onPersonRemove,
                            onChangeGlyph = onPersonGlyphSheet,
                            onContinue = onNext,
                        )
                    "pace" -> PaceStep(state.pace, state.isCompleting, state.errorRes, onPaceSelect, onNext)
                    // `handoff` has no screen (D9): `isDone` pops the entry before it is ever the step.
                    else -> Box(Modifier.fillMaxSize())
                }
            }
        }
        // S2: the soul glyph sheet, over whichever step is showing (only step 5 opens it).
        state.glyphSheetFor?.let { index ->
            val person = state.people.getOrNull(index) ?: return@let
            SoulGlyphSheet(
                personName = person.name,
                glyphs = state.systemGlyphs + state.carved,
                currentGlyphId = person.glyphId,
                onUse = { onPersonGlyphPick(index, it) },
                onDraw = { onDrawForPerson(index) },
                onDismiss = { onPersonGlyphSheet(null) },
            )
        }
    }
}

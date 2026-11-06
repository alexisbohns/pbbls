package app.pbbls.android.features.path.complete

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.pbbls.android.core.model.Domain
import app.pbbls.android.core.model.PebbleAnswers
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.features.path.flow.steps.RecordEmotionStep
import app.pbbls.android.features.path.flow.steps.RecordLifeStep
import app.pbbls.android.features.path.flow.steps.RecordValenceStep

/**
 * The body of a Complete step, on the shared stateless step bodies (`flow/steps`).
 * Reads come from [answers]; every write goes through [model] so it buzzes
 * (M58 D4). Exhaustive with no `else`, so a new step cannot render nothing.
 *
 * LIFE is not a scaffold body: it draws its own title and bar, so [CompleteFlowContent] calls this
 * for it outside `RecordStepScaffold`.
 */
@Composable
fun CompleteStepContent(
    step: CompleteStep,
    answers: PebbleAnswers,
    model: CompleteFlowModel,
    domains: List<Domain>,
    recent: List<Domain>,
    souls: List<SoulWithGlyph>,
    isSoulsLoaded: Boolean,
    error: String?,
    isCompleting: Boolean,
    hapticsOn: Boolean,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    isFirstPebble: Boolean = false,
    valenceHelpOpen: Boolean = false,
    onFirstPebbleHelp: () -> Unit = {},
    onOpenValenceHelp: () -> Unit = {},
    onCloseValenceHelp: () -> Unit = {},
    onAchievementCheck: () -> Unit = {},
) {
    when (step) {
        CompleteStep.VALENCE ->
            RecordValenceStep(
                selected = answers.valence,
                onSelect = { model.selectValence(it) },
                isFirstPebble = isFirstPebble,
                helpOpen = valenceHelpOpen,
                onFirstPebbleHelp = onFirstPebbleHelp,
                onOpenHelp = onOpenValenceHelp,
                onCloseHelp = onCloseValenceHelp,
                modifier = modifier,
            )

        CompleteStep.EMOTION ->
            RecordEmotionStep(
                selectedId = answers.emotionId,
                valence = answers.valence,
                onSelect = { model.selectEmotion(it) },
                modifier = modifier,
            )

        CompleteStep.LIFE ->
            RecordLifeStep(
                answers = answers,
                domains = domains,
                recent = recent,
                souls = souls,
                isSoulsLoaded = isSoulsLoaded,
                error = error,
                isCompleting = isCompleting,
                hapticsOn = hapticsOn,
                onToggleDomain = model::toggleDomain,
                onToggleSoul = model::toggleSoul,
                onSetSouls = model::setSouls,
                onComplete = onComplete,
                modifier = modifier,
                onAchievementCheck = onAchievementCheck,
            )

        // Rendered by CompleteFlowContent outside the scaffold.
        CompleteStep.SUCCESS -> Unit
    }
}

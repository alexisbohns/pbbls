package app.pbbls.android.features.path.complete

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.pbbls.android.core.model.Domain
import app.pbbls.android.core.model.PebbleAnswers
import app.pbbls.android.core.model.PebbleCollection
import app.pbbls.android.features.path.flow.steps.RecordCollectionStep
import app.pbbls.android.features.path.flow.steps.RecordDomainStep
import app.pbbls.android.features.path.flow.steps.RecordEmotionStep
import app.pbbls.android.features.path.flow.steps.RecordPrivacyStep
import app.pbbls.android.features.path.flow.steps.RecordSoulsStep
import app.pbbls.android.features.path.flow.steps.RecordValenceStep

/**
 * The body of a Complete step, on the shared stateless step bodies (`flow/steps`).
 * Reads come from [answers]; every write goes through [model] so it buzzes
 * (M58 D4). Exhaustive with no `else`, so a new step cannot render nothing.
 */
@Composable
fun CompleteStepContent(
    step: CompleteStep,
    answers: PebbleAnswers,
    model: CompleteFlowModel,
    domains: List<Domain>,
    collections: List<PebbleCollection>,
    error: String?,
    modifier: Modifier = Modifier,
    onAchievementCheck: () -> Unit = {},
) {
    when (step) {
        CompleteStep.VALENCE ->
            RecordValenceStep(
                selected = answers.valence,
                onSelect = { model.selectValence(it) },
                onSeed = { model.seedValenceIfNeeded() },
                modifier = modifier,
            )

        CompleteStep.EMOTION ->
            RecordEmotionStep(
                selectedId = answers.emotionId,
                valence = answers.valence,
                onSelect = { model.selectEmotion(it) },
                modifier = modifier,
            )

        CompleteStep.DOMAIN ->
            RecordDomainStep(
                domains = domains,
                selectedId = answers.domainId,
                onSelect = { model.selectDomain(it) },
                modifier = modifier,
            )

        CompleteStep.SOULS ->
            RecordSoulsStep(
                selectedIds = answers.soulIds,
                onToggle = { model.toggleSoul(it) },
                modifier = modifier,
                onAchievementCheck = onAchievementCheck,
            )

        CompleteStep.COLLECTION ->
            RecordCollectionStep(
                collections = collections,
                selectedId = answers.collectionId,
                onSelect = { model.selectCollection(it) },
                modifier = modifier,
            )

        CompleteStep.PRIVACY ->
            RecordPrivacyStep(
                selected = answers.visibility,
                onSelect = { model.selectVisibility(it) },
                modifier = modifier,
                publishError = error,
            )

        // Rendered by CompleteFlowContent outside the scaffold.
        CompleteStep.SUCCESS -> Unit
    }
}

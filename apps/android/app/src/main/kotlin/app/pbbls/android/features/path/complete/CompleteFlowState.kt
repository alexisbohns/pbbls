package app.pbbls.android.features.path.complete

import androidx.annotation.StringRes
import app.pbbls.android.core.model.PebbleAnswers
import app.pbbls.android.core.model.PebbleGlyph

data class CompleteResult(
    val pebbleId: String,
    val karmaDelta: Int?,
    val glyph: PebbleGlyph?,
)

data class CompleteFlowState(
    val answers: PebbleAnswers = PebbleAnswers(),
    val step: CompleteStep = CompleteStep.VALENCE,
    val completed: CompleteResult? = null,
    val isCompleting: Boolean = false,
    @StringRes val errorRes: Int? = null,
) {
    /** Exhaustive with no `else`: a new step must say what "answered" means. */
    fun hasAnswer(forStep: CompleteStep): Boolean =
        when (forStep) {
            CompleteStep.SUCCESS -> true
            CompleteStep.VALENCE -> answers.valence != null
            CompleteStep.EMOTION -> answers.emotionId != null
            CompleteStep.LIFE -> answers.domainIds.isNotEmpty()
        }

    val isAnswered: Boolean get() = hasAnswer(step)

    fun firstGap(): CompleteStep = CompleteStep.counted.firstOrNull { !hasAnswer(it) } ?: CompleteStep.LIFE
}

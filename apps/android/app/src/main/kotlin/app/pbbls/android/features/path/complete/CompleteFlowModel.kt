package app.pbbls.android.features.path.complete

import androidx.annotation.StringRes
import app.pbbls.android.core.data.TapHaptic
import app.pbbls.android.core.model.PebbleAnswers
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.Visibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The Complete flow's state machine. Every public mutator buzzes except the
 * silent seed, restore and [goTo]. [haptic] has no default, as in
 * `RecordFlowModel` (M58 D4).
 */
class CompleteFlowModel(
    private val haptic: (TapHaptic) -> Unit,
    initial: CompleteFlowState = CompleteFlowState(),
) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<CompleteFlowState> = _state.asStateFlow()

    val answers: PebbleAnswers get() = _state.value.answers
    val step: CompleteStep get() = _state.value.step
    val isCompleting: Boolean get() = _state.value.isCompleting
    val completed: CompleteResult? get() = _state.value.completed

    fun advance() {
        if (step == CompleteStep.SUCCESS) return
        if (!_state.value.isAnswered) {
            haptic(TapHaptic.WARNING)
            return
        }
        val next = step.next ?: return
        haptic(TapHaptic.ADVANCE)
        _state.update { it.copy(step = next) }
    }

    fun back() {
        if (step == CompleteStep.SUCCESS) return
        val previous = step.previous ?: return
        haptic(TapHaptic.ADVANCE)
        _state.update { it.copy(step = previous) }
    }

    /** Silent jump, for restore and tests. Never wired to a control. */
    fun goTo(target: CompleteStep) = _state.update { it.copy(step = target) }

    fun seedValenceIfNeeded() {
        if (answers.valence != null) return
        _state.update { it.copy(answers = it.answers.copy(valence = Valence.NEUTRAL_MEDIUM)) }
    }

    fun selectValence(valence: Valence) = select { it.copy(valence = valence) }

    fun selectEmotion(emotionId: String) = commitAndAdvance { it.copy(emotionId = emotionId) }

    fun selectDomain(domainId: String) = commitAndAdvance { it.copy(domainId = domainId) }

    fun selectCollection(collectionId: String) = commitAndAdvance { it.copy(collectionId = collectionId) }

    fun toggleSoul(id: String) = select { a -> a.copy(soulIds = if (id in a.soulIds) a.soulIds - id else a.soulIds + id) }

    fun selectVisibility(visibility: Visibility) = select { it.copy(visibility = visibility) }

    /** Silent: restoring is not something the user did. */
    fun resume(restored: PebbleAnswers) =
        _state.update { current ->
            val hydrated = current.copy(answers = restored)
            hydrated.copy(step = hydrated.firstGap())
        }

    fun beginComplete() = _state.update { it.copy(isCompleting = true, errorRes = null) }

    fun succeed(result: CompleteResult) {
        haptic(TapHaptic.SUCCESS)
        _state.update { it.copy(isCompleting = false, completed = result, step = CompleteStep.SUCCESS) }
    }

    fun fail(
        @StringRes messageRes: Int,
    ) {
        haptic(TapHaptic.WARNING)
        _state.update { it.copy(isCompleting = false, errorRes = messageRes) }
    }

    private fun select(mutate: (PebbleAnswers) -> PebbleAnswers) {
        haptic(TapHaptic.SELECTION)
        _state.update { it.copy(answers = mutate(it.answers)) }
    }

    /** One emission for the commit and the advance, and one buzz for one tap. */
    private fun commitAndAdvance(mutate: (PebbleAnswers) -> PebbleAnswers) {
        haptic(TapHaptic.SELECTION)
        _state.update { it.copy(answers = mutate(it.answers), step = it.step.next ?: it.step) }
    }
}

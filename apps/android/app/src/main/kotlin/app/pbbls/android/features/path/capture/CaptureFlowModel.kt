package app.pbbls.android.features.path.capture

import app.pbbls.android.core.data.TapHaptic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.OffsetDateTime

/** Capture's state machine; every interaction buzzes (M58 D4). [haptic] has no default. */
class CaptureFlowModel(
    private val haptic: (TapHaptic) -> Unit,
    initial: CaptureFlowState = CaptureFlowState(),
) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<CaptureFlowState> = _state.asStateFlow()

    val draft: CaptureDraft get() = _state.value.draft
    val step: CaptureStep get() = _state.value.step
    val isFrozen: Boolean get() = _state.value.isFrozen

    var hasSnap: Boolean
        get() = _state.value.hasSnap
        set(value) = _state.update { it.copy(hasSnap = value) }

    fun advance() {
        if (isFrozen) return
        if (!_state.value.isAnswered) {
            haptic(TapHaptic.WARNING)
            return
        }
        val next = step.next ?: return
        haptic(TapHaptic.ADVANCE)
        _state.update { it.copy(step = next) }
    }

    /** The chrome's back arrow calls this directly, so the save guard lives here. */
    fun back() {
        if (isFrozen) return
        val previous = step.previous ?: return
        haptic(TapHaptic.ADVANCE)
        _state.update { it.copy(step = previous) }
    }

    fun goTo(target: CaptureStep) = _state.update { it.copy(step = target) }

    /** Front-end clamp only (M58 D3); the server does not constrain names. */
    fun setName(raw: String) = _state.update { it.copy(draft = it.draft.copy(name = raw.take(NAME_LIMIT)), errorRes = null) }

    fun setHappenedAt(value: OffsetDateTime) = _state.update { it.copy(draft = it.draft.copy(happenedAt = value)) }

    fun applyCaptureDate(date: OffsetDateTime?) {
        if (date != null) setHappenedAt(date)
    }

    fun beginSave() = _state.update { it.copy(isSaving = true, errorRes = null) }

    fun saved() {
        haptic(TapHaptic.SUCCESS)
        // Stays frozen: navigation happens one hop later, through the effect channel.
        _state.update { it.copy(isSaving = false, isSaved = true) }
    }

    /**
     * A save can start from the close dialog on any step, and its error shows
     * only on the name step, so a failure lands there to be seen (iOS `failSave`).
     */
    fun fail(messageRes: Int) {
        haptic(TapHaptic.WARNING)
        _state.update { it.copy(step = CaptureStep.NAME, isSaving = false, errorRes = messageRes) }
    }

    companion object {
        const val NAME_LIMIT = 40
    }
}

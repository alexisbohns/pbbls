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

    /** Front-end clamp only, like the name: three visible lines and a counter. */
    fun setDescription(raw: String) =
        _state.update { it.copy(draft = it.draft.copy(description = raw.take(DESCRIPTION_LIMIT)), errorRes = null) }

    /** The user's own edit of the when chip: from here a photo's date never overrides it (D4). */
    fun setHappenedAt(value: OffsetDateTime) = _state.update { it.copy(draft = it.draft.copy(happenedAt = value), hasEditedDate = true) }

    /** Seeds when from a photo's date (EXIF or the shutter), unless the user already chose one. */
    fun applyCaptureDate(date: OffsetDateTime?) {
        if (date == null) return
        _state.update { if (it.hasEditedDate) it else it.copy(draft = it.draft.copy(happenedAt = date), seededFromPhoto = true) }
    }

    fun beginSave(thenComplete: Boolean = false) =
        _state.update { it.copy(isSaving = true, savingThenComplete = thenComplete, errorRes = null) }

    fun saved() {
        haptic(TapHaptic.SUCCESS)
        // Stays frozen: navigation happens one hop later, through the effect channel.
        _state.update { it.copy(isSaving = false, isSaved = true) }
    }

    /** A save can start from the close dialog on any step, and its error shows on the page, so a failure lands there to be seen. */
    fun fail(messageRes: Int) {
        haptic(TapHaptic.WARNING)
        _state.update { it.copy(step = CaptureStep.MOMENT, isSaving = false, savingThenComplete = false, errorRes = messageRes) }
    }

    companion object {
        const val NAME_LIMIT = 40
        const val DESCRIPTION_LIMIT = 280
    }
}

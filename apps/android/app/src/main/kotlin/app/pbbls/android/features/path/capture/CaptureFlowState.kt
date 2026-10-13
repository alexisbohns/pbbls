package app.pbbls.android.features.path.capture

import androidx.annotation.StringRes
import java.time.OffsetDateTime

data class CaptureDraft(
    val name: String = "",
    val happenedAt: OffsetDateTime = OffsetDateTime.now(),
)

data class CaptureFlowState(
    val draft: CaptureDraft = CaptureDraft(),
    val step: CaptureStep = CaptureStep.PHOTO,
    /** Mirrored from the upload coordinator, so the flow never owns media. */
    val hasSnap: Boolean = false,
    val isSaving: Boolean = false,
    /** Terminal: the moment exists, and the form waits only for the screen to leave. */
    val isSaved: Boolean = false,
    @StringRes val errorRes: Int? = null,
) {
    fun hasAnswer(forStep: CaptureStep): Boolean =
        when (forStep) {
            CaptureStep.PHOTO -> hasSnap
            CaptureStep.WHEN -> true
            CaptureStep.NAME -> draft.name.isNotBlank() || hasSnap
        }

    /** Saving or saved: no step change, photo change, save or close until the screen leaves. */
    val isFrozen: Boolean get() = isSaving || isSaved

    val isAnswered: Boolean get() = step.isOptional || hasAnswer(step)

    /** A moment needs a date and a name or a photo (D3). */
    val canSave: Boolean get() = !isFrozen && hasAnswer(CaptureStep.NAME)

    /** Something the user would lose by leaving: a typed name or a photo. */
    val hasInput: Boolean get() = draft.name.isNotBlank() || hasSnap

    /** The photo step's quiet button reads Skip until a photo is attached. */
    val photoButtonIsSkip: Boolean get() = !hasSnap
}

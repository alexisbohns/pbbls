package app.pbbls.android.features.path.capture

import androidx.annotation.StringRes
import java.time.OffsetDateTime

/** Where the when chip's date came from (D4): it reads "From your photo" only for [PHOTO]. */
enum class WhenSource { NOW, PHOTO, EDITED }

data class CaptureDraft(
    val name: String = "",
    val description: String = "",
    val happenedAt: OffsetDateTime = OffsetDateTime.now(),
)

data class CaptureFlowState(
    val draft: CaptureDraft = CaptureDraft(),
    val step: CaptureStep = CaptureStep.PHOTO,
    /** Mirrored from the upload coordinator, so the flow never owns media. */
    val hasSnap: Boolean = false,
    val isSaving: Boolean = false,
    /** Whether the save in flight is the one that goes on to Complete: it picks which button spins. */
    val savingThenComplete: Boolean = false,
    /** Terminal: the moment exists, and the form waits only for the screen to leave. */
    val isSaved: Boolean = false,
    /** A user edit always wins over the photo's date (D4). */
    val hasEditedDate: Boolean = false,
    val seededFromPhoto: Boolean = false,
    @StringRes val errorRes: Int? = null,
) {
    /** Saving or saved: no step change, photo change, save or close until the screen leaves. */
    val isFrozen: Boolean get() = isSaving || isSaved

    /** D3: a moment needs a name or a photo. Words alone do not make one. */
    val canSave: Boolean get() = !isFrozen && (draft.name.isNotBlank() || hasSnap)

    /** Words count, unlike on iOS: leaving must never silently lose what was typed. */
    val hasInput: Boolean get() = draft.name.isNotBlank() || draft.description.isNotBlank() || hasSnap

    val whenSource: WhenSource
        get() =
            when {
                hasEditedDate -> WhenSource.EDITED
                seededFromPhoto -> WhenSource.PHOTO
                else -> WhenSource.NOW
            }
}

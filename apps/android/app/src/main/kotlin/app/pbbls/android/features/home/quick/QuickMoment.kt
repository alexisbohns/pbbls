package app.pbbls.android.features.home.quick

import android.util.Log
import androidx.annotation.StringRes
import app.pbbls.android.R
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.AchievementsServicing
import app.pbbls.android.core.data.MomentWriteServicing
import app.pbbls.android.core.model.MomentSavePayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

private const val TAG = "home-quick"

/** What the user asked for after the quick moment saved (iOS `QuickMomentExit`). */
enum class QuickMomentExit {
    /** "Save & open": Complete opens for the new moment. */
    OPEN,

    /** "Save & close": back to Home. */
    CLOSE,
}

/**
 * The open quick moment sheet's form (#1383 D7; iOS `QuickMomentDraft`). [name] is what the field
 * shows, clamped to Capture's limit; what is saved is it trimmed. [happenedAt] starts at now.
 */
data class QuickMomentUiState(
    val name: String = "",
    val happenedAt: OffsetDateTime,
    val isSaving: Boolean = false,
    @StringRes val errorRes: Int? = null,
) {
    /** A savable name: trimmed, not blank (iOS `canSave`). */
    val trimmedName: String get() = name.trim()

    /** Both saves are off while the name is blank or a save runs. */
    val canSave: Boolean get() = trimmedName.isNotEmpty() && !isSaving
}

/**
 * Home's quick moment (#1383 D7; iOS `QuickMomentSheet`): a name and a time, saved with one
 * `save_moment`, then `fireCheck()`. Not a navigation key: the sheet is a `ModalBottomSheet` over
 * Home, so this is a plain state holder owned by [app.pbbls.android.features.home.HomeMomentsViewModel]
 * and running its writes in that ViewModel's scope, which outlives the sheet ([sheet] null = closed).
 *
 * A save leaves the device inside `NonCancellable` (`apps/android/CLAUDE.md`): once sent, it finishes
 * and reports even if Home's entry goes away. Success closes the sheet and hands [onSaved] the id and
 * the exit; failure keeps the sheet open with "Couldn't save the moment. Try again." (iOS copy).
 */
class QuickMoment(
    private val scope: CoroutineScope,
    private val writes: MomentWriteServicing,
    private val achievements: AchievementsServicing,
    private val onSaved: (momentId: String, exit: QuickMomentExit) -> Unit,
    private val now: () -> OffsetDateTime = OffsetDateTime::now,
) {
    private val _sheet = MutableStateFlow<QuickMomentUiState?>(null)

    /** The open sheet's form, or null while the sheet is closed. */
    val sheet: StateFlow<QuickMomentUiState?> = _sheet.asStateFlow()

    /** A fresh form at now, each time the sheet opens. A sheet already open keeps its form. */
    fun open() {
        if (_sheet.value == null) _sheet.value = QuickMomentUiState(happenedAt = now())
    }

    /** The sheet went away (swipe, scrim, back). A save in flight still lands and reports. */
    fun dismiss() {
        _sheet.value = null
    }

    /** Front-end clamp only, as Capture's (M58 D3): the server does not constrain names. */
    fun setName(raw: String) = _sheet.update { it?.copy(name = raw.take(NAME_LIMIT), errorRes = null) }

    fun setHappenedAt(value: OffsetDateTime) = _sheet.update { it?.copy(happenedAt = value) }

    fun save(exit: QuickMomentExit) {
        val form = _sheet.value ?: return
        if (!form.canSave) return
        _sheet.value = form.copy(isSaving = true, errorRes = null)
        // Whole seconds: the cross-surface timestamp rule (iOS floors the same way).
        val payload = MomentSavePayload(name = form.trimmedName, happenedAt = form.happenedAt.truncatedTo(ChronoUnit.SECONDS))
        scope.launch {
            withContext(NonCancellable) {
                runCatchingCancellable { writes.saveMoment(payload) }.fold(
                    onSuccess = { momentId ->
                        achievements.fireCheck()
                        _sheet.value = null
                        onSaved(momentId, exit)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "quick save_moment failed", error)
                        _sheet.update { it?.copy(isSaving = false, errorRes = R.string.home_quick_error) }
                    },
                )
            }
        }
    }

    companion object {
        /** Capture's name limit (`CaptureFlowModel.NAME_LIMIT`), so the two flows agree; a test pins it. */
        const val NAME_LIMIT = 40
    }
}

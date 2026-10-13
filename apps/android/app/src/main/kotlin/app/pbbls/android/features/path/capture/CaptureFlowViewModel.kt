package app.pbbls.android.features.path.capture

import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.core.common.UiEffects
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.MomentWriteServicing
import app.pbbls.android.core.data.SnapUploadCoordinator
import app.pbbls.android.core.data.SnapWriteRepositing
import app.pbbls.android.core.data.SupabaseServicing
import app.pbbls.android.core.data.TapHaptic
import app.pbbls.android.core.data.toDataError
import app.pbbls.android.core.model.FormSnap
import app.pbbls.android.core.model.MomentSavePayload
import app.pbbls.android.core.model.PebbleSnapPayload
import app.pbbls.android.core.ui.momentWriteErrorRes
import app.pbbls.android.features.path.ComposerMedia
import app.pbbls.android.features.path.PathFocusRequests
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject

private const val TAG = "capture-flow"
private const val KEY_STEP = "capture-step"
private const val KEY_NAME = "capture-name"
private const val KEY_WHEN = "capture-when-epoch"

sealed interface CaptureFlowEffect {
    data class Haptic(
        val flavor: TapHaptic,
    ) : CaptureFlowEffect

    data object Saved : CaptureFlowEffect

    data class SavedThenComplete(
        val momentId: String,
    ) : CaptureFlowEffect

    data object Dismiss : CaptureFlowEffect
}

data class CaptureFlowUiState(
    val flow: CaptureFlowState = CaptureFlowState(),
    val snap: FormSnap? = null,
    val seededFromPhoto: Boolean = false,
    /** Non-null while the attached photo blocks saving: still uploading, or failed. */
    @StringRes val snapBlockedMessageRes: Int? = null,
    val isCloseConfirmPresented: Boolean = false,
    /** The close dialog offers Save moment only when the moment is savable now (R2). */
    val closeOffersSave: Boolean = false,
)

/**
 * Capture: a moment in three steps, saved with `save_moment` (A-2). Nothing
 * is kept across a crash but what `SavedStateHandle` holds (step, name, date):
 * the server has not seen them, and a three-step form needs no autosave (A-5).
 */
@HiltViewModel
class CaptureFlowViewModel
    @Inject
    constructor(
        private val savedState: SavedStateHandle,
        private val writes: MomentWriteServicing,
        private val supabase: SupabaseServicing,
        private val media: ComposerMedia,
        private val focusRequests: PathFocusRequests,
        snapRepo: SnapWriteRepositing,
    ) : ViewModel() {
        private val effectsOut = UiEffects<CaptureFlowEffect>(viewModelScope)
        val effects: Flow<CaptureFlowEffect> = effectsOut.flow

        private val model =
            CaptureFlowModel(
                haptic = { effectsOut.emit(CaptureFlowEffect.Haptic(it)) },
                initial =
                    CaptureFlowState(
                        draft =
                            CaptureDraft(
                                name = savedState.get<String>(KEY_NAME).orEmpty(),
                                happenedAt =
                                    savedState.get<Long>(KEY_WHEN)?.let {
                                        OffsetDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneId.systemDefault())
                                    } ?: OffsetDateTime.now(),
                            ),
                        step =
                            savedState.get<String>(KEY_STEP)?.let { saved -> CaptureStep.entries.firstOrNull { it.name == saved } }
                                ?: CaptureStep.PHOTO,
                    ),
            )

        /** Form-scoped (M42 D6), held by the ViewModel so a rotation mid-upload keeps the upload. */
        private val snaps = SnapUploadCoordinator(repo = snapRepo)

        private val _uiState = MutableStateFlow(CaptureFlowUiState(flow = model.state.value))
        val uiState: StateFlow<CaptureFlowUiState> = _uiState.asStateFlow()

        private val userId: String?
            get() = supabase.session?.user?.id

        init {
            viewModelScope.launch {
                model.state.collect { flow ->
                    savedState[KEY_STEP] = flow.step.name
                    savedState[KEY_NAME] = flow.draft.name
                    savedState[KEY_WHEN] = flow.draft.happenedAt.toEpochSecond()
                    _uiState.update { it.copy(flow = flow) }
                }
            }
            viewModelScope.launch {
                snapshotFlow { snaps.formSnap }.collect { snap ->
                    model.hasSnap = snap != null
                    _uiState.update { it.copy(snap = snap, snapBlockedMessageRes = blockedBySnap()) }
                }
            }
        }

        /** Every tap goes through the machine (M58 D4). */
        fun machine(): CaptureFlowModel = model

        fun onPhotoPicked(uri: Uri) {
            if (model.isSaving) return
            val id = userId ?: return
            viewModelScope.launch {
                runCatchingCancellable {
                    // EXIF first: the re-encode writes no metadata (M58 D7).
                    val taken = media.captureDate(uri)
                    _uiState.update { it.copy(seededFromPhoto = taken != null) }
                    model.applyCaptureDate(taken)
                    snaps.attach(media.process(uri), id)
                }.onFailure { Log.e(TAG, "photo pick processing failed", it) }
            }
        }

        fun onRetryPhoto() {
            val id = userId ?: return
            viewModelScope.launch { snaps.retryCurrent(id) }
        }

        fun onRemovePhoto() {
            if (model.isSaving) return
            val id = userId ?: return
            viewModelScope.launch { snaps.removePending(id) }
        }

        fun save(thenComplete: Boolean) {
            if (model.isSaving) return
            val id = userId
            if (id == null) {
                Log.e(TAG, "save: no current user")
                model.fail(R.string.record_signed_out_error)
                return
            }
            blockedBySnap()?.let {
                model.fail(it)
                return
            }
            if (!model.state.value.canSave) return
            val draft = model.draft
            val snap = snaps.pendingSnapForPayload()?.let { PebbleSnapPayload(it.id, it.storagePrefix(id), 0) }
            model.beginSave()
            viewModelScope.launch {
                // Popping the entry must not abort a write the server may have accepted (#852).
                withContext(NonCancellable) {
                    runCatchingCancellable { writes.saveMoment(MomentSavePayload.capture(draft.name, draft.happenedAt, snap)) }
                        .fold(
                            onSuccess = { momentId ->
                                model.saved()
                                // The files now belong to the moment: forget them without deleting.
                                snaps.reset()
                                // Path shows the moment's week when it is next on screen (R14).
                                focusRequests.request(momentId)
                                effectsOut.emit(if (thenComplete) CaptureFlowEffect.SavedThenComplete(momentId) else CaptureFlowEffect.Saved)
                            },
                            onFailure = {
                                Log.e(TAG, "save_moment failed", it)
                                // Files stay for the retry; discarding is the cleanup (A-3).
                                model.fail(momentWriteErrorRes(it.toDataError()))
                            },
                        )
                }
            }
        }

        @StringRes
        private fun blockedBySnap(): Int? =
            when {
                snaps.isUploading -> R.string.pebble_save_error_photo_uploading
                snaps.hasFailed -> R.string.pebble_save_error_photo_failed
                else -> null
            }

        /**
         * Asks only when there is something to lose (R2): "Keep this moment?"
         * with Save moment (when savable and the photo is not blocking),
         * Discard and Keep going.
         */
        fun onCloseRequested() {
            if (model.isSaving) return
            val state = model.state.value
            if (state.hasInput) {
                val offersSave = state.canSave && blockedBySnap() == null
                _uiState.update { it.copy(isCloseConfirmPresented = true, closeOffersSave = offersSave) }
            } else {
                discardAndClose()
            }
        }

        fun onKeepGoing() = _uiState.update { it.copy(isCloseConfirmPresented = false) }

        fun onSaveFromClose() {
            _uiState.update { it.copy(isCloseConfirmPresented = false) }
            save(thenComplete = false)
        }

        fun onDiscard() {
            _uiState.update { it.copy(isCloseConfirmPresented = false) }
            discardAndClose()
        }

        private fun discardAndClose() {
            viewModelScope.launch {
                userId?.let { snaps.cancelAndCleanup(it) }
                effectsOut.emit(CaptureFlowEffect.Dismiss)
            }
        }

        /** Back walks the steps; on the photo step it is ✕ (R2). */
        fun onSystemBack() {
            when {
                model.isSaving -> Unit
                model.step.previous != null -> model.back()
                else -> onCloseRequested()
            }
        }
    }

package app.pbbls.android.features.path

import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.core.common.UiEffects
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.HapticsPreference
import app.pbbls.android.core.data.MomentReadServicing
import app.pbbls.android.core.data.MomentWriteServicing
import app.pbbls.android.core.data.SnapUploadCoordinator
import app.pbbls.android.core.data.SnapWriteRepositing
import app.pbbls.android.core.data.SupabaseServicing
import app.pbbls.android.core.data.toDataError
import app.pbbls.android.core.model.FormSnap
import app.pbbls.android.core.model.MomentSavePayload
import app.pbbls.android.core.model.PebbleDraft
import app.pbbls.android.core.model.PebbleSnapPayload
import app.pbbls.android.core.ui.momentWriteErrorRes
import app.pbbls.android.features.path.crop.CropPickGate
import app.pbbls.android.features.path.crop.CropSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "edit-moment"

sealed interface EditMomentUiState {
    data object Loading : EditMomentUiState

    data object Error : EditMomentUiState

    data class Content(
        /** Only when, name and description are read here; the pebble half is Edit pebble's. */
        val draft: PebbleDraft,
        val snap: FormSnap? = null,
        val isSaving: Boolean = false,
        @StringRes val saveErrorRes: Int? = null,
        /** The photo open in the crop editor (#1659), drawn over the form; null when the editor is closed. */
        val cropping: CropSession? = null,
    ) : EditMomentUiState {
        /** A name or a photo (D3); the screen shows `edit_moment_needs_content` while false (R6). */
        val hasContent: Boolean by lazy { draft.name.isNotBlank() || snap != null }
        val canSave: Boolean by lazy { !isSaving && hasContent }
    }
}

sealed interface EditMomentEffect {
    data object Saved : EditMomentEffect

    data object Dismissed : EditMomentEffect
}

/** Edit moment (R5): when, name, description and photo, written with `save_moment` and nothing else. */
@HiltViewModel
class EditMomentViewModel
    @Inject
    constructor(
        private val moments: MomentReadServicing,
        private val writes: MomentWriteServicing,
        private val supabase: SupabaseServicing,
        private val snapFiles: SnapWriteRepositing,
        private val media: ComposerMedia,
        private val haptics: HapticsPreference,
    ) : ViewModel() {
        private val effectsOut = UiEffects<EditMomentEffect>(viewModelScope)
        val effects: Flow<EditMomentEffect> = effectsOut.flow

        /** Pebbles' haptics switch (#1242 D23), for the form's pickers: asked at each buzz, so Settings applies at once. */
        val hapticsOn: Boolean get() = haptics.isOn

        private val snaps = SnapUploadCoordinator(repo = snapFiles)
        private val _uiState = MutableStateFlow<EditMomentUiState>(EditMomentUiState.Loading)
        val uiState: StateFlow<EditMomentUiState> = _uiState.asStateFlow()

        private var loadedMomentId: String? = null
        private var detachedSnap: FormSnap.Existing? = null

        /**
         * Which pick the form is waiting on, from its tap until Done has handed it to the coordinator (#1659):
         * Save waits meanwhile, a later pick supersedes an earlier one, and a read that lands after Cancel or a
         * dismissal opens nothing.
         */
        private val cropGate = CropPickGate()

        /** The latest pick's read, held so a dismissal can stop it. */
        private var loadJob: Job? = null

        private val userId: String?
            get() = supabase.session?.user?.id

        private val content: EditMomentUiState.Content?
            get() = _uiState.value as? EditMomentUiState.Content

        init {
            viewModelScope.launch {
                snapshotFlow { snaps.formSnap }.collect { snap -> updateContent { it.copy(snap = snap) } }
            }
        }

        /** Guarded: a rotation re-runs the screen's `LaunchedEffect` and must not discard edits. */
        fun start(momentId: String) {
            if (loadedMomentId == momentId) return
            loadedMomentId = momentId
            snaps.reset()
            load(momentId)
        }

        fun retry() = loadedMomentId?.let { load(it) }

        private fun load(momentId: String) {
            _uiState.value = EditMomentUiState.Loading
            viewModelScope.launch {
                runCatchingCancellable { moments.loadMoment(momentId) }
                    .fold(
                        onSuccess = { detail ->
                            // At most one photo (M42 D1).
                            val existing = detail.sortedSnaps.firstOrNull()?.let { FormSnap.Existing(it.id, it.storagePath) }
                            snaps.seedExisting(existing)
                            _uiState.value = EditMomentUiState.Content(draft = PebbleDraft.from(detail), snap = existing)
                        },
                        onFailure = {
                            Log.e(TAG, "edit moment load failed", it)
                            _uiState.value = EditMomentUiState.Error
                        },
                    )
            }
        }

        /**
         * True while a save is in flight. The form holds still until it lands: the
         * payload is already sent, and a photo change now would delete files the
         * server row may point to.
         */
        private val isSaving: Boolean
            get() = content?.isSaving == true

        /** The name is clamped only when it is the field being edited: a longer legacy name survives a date change. */
        fun onDraftChange(draft: PebbleDraft) {
            if (isSaving) return
            updateContent {
                val name = if (draft.name == it.draft.name) draft.name else draft.name.take(NAME_LIMIT)
                it.copy(draft = draft.copy(name = name), saveErrorRes = null)
            }
        }

        /** A pick: read for the crop editor, which opens once the bytes are in (#1659). */
        fun onPhotoPicked(uri: Uri) {
            if (isSaving || userId == null) return
            val token = cropGate.begin()
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    val source =
                        runCatchingCancellable { media.cropSource(uri) }
                            .onFailure { Log.e(TAG, "photo could not be read for the crop editor", it) }
                            .getOrNull()
                    if (!cropGate.finishLoading(token, opened = source != null)) {
                        Log.i(TAG, "crop load dropped: superseded or closed while loading")
                        return@launch
                    }
                    if (source != null) updateContent { it.copy(cropping = CropSession(source, token)) }
                }
        }

        /** Cancel in the editor, or back: nothing is attached. */
        fun onCropCancelled() {
            val session = content?.cropping ?: return
            cropGate.release(session.token)
            updateContent { it.copy(cropping = null) }
        }

        /**
         * Done in the editor: what the window shows is rendered, processed and attached. The token is held until
         * the coordinator has it, so Save keeps waiting through that gap.
         */
        fun onCropDone() {
            val session = content?.cropping ?: return
            updateContent { it.copy(cropping = null) }
            val id = userId
            if (isSaving || id == null) {
                cropGate.release(session.token)
                return
            }
            val geometry = session.state.geometry.normalized()
            // At Done, so a slower earlier photo never lands over a later one.
            val generation = snaps.nextPickGeneration()
            viewModelScope.launch(start = CoroutineStart.ATOMIC) {
                try {
                    runCatchingCancellable { snaps.attach(media.processCropped(session.source, geometry), id, generation) }
                        .onFailure { Log.e(TAG, "cropped photo processing failed", it) }
                } finally {
                    cropGate.release(session.token)
                }
            }
        }

        fun onRetryPhoto() {
            val id = userId ?: return
            viewModelScope.launch { snaps.retryCurrent(id) }
        }

        fun onRemovePhoto() {
            if (isSaving) return
            val id = userId ?: return
            viewModelScope.launch { snaps.removePending(id) }
        }

        /** Detaches only; the save removes the row and then the files (R6). */
        fun onRemoveExistingSnap() {
            if (isSaving) return
            detachedSnap = snaps.detachExisting() ?: detachedSnap
            updateContent { it.copy(snap = snaps.formSnap) }
        }

        /** Back: the editor's Cancel while it is open, else a dismissal. */
        fun onSystemBack() {
            if (content?.cropping != null) onCropCancelled() else dismiss()
        }

        fun dismiss() {
            if (content?.isSaving == true) return
            // A photo still loading or open in the editor opens nothing once the form is gone.
            cropGate.clear()
            loadJob?.cancel()
            updateContent { it.copy(cropping = null) }
            viewModelScope.launch {
                userId?.let { snaps.cancelAndCleanup(it) }
                effectsOut.emit(EditMomentEffect.Dismissed)
            }
        }

        fun save() {
            val state = content ?: return
            if (!state.canSave) return
            if (snaps.isUploading || cropGate.isActive) {
                updateContent { it.copy(saveErrorRes = R.string.pebble_save_error_photo_uploading) }
                return
            }
            if (snaps.hasFailed) {
                updateContent { it.copy(saveErrorRes = R.string.pebble_save_error_photo_failed) }
                return
            }
            val momentId = loadedMomentId ?: return
            val id = userId
            // The whole set: existing echoed, a fresh upload as its pair, [] when removed.
            val snapPayload =
                when (val formSnap = snaps.formSnap) {
                    is FormSnap.Existing -> listOf(PebbleSnapPayload(formSnap.id, formSnap.storagePath, 0))
                    is FormSnap.Pending ->
                        snaps.pendingSnapForPayload()?.let { snap ->
                            id?.let { listOf(PebbleSnapPayload(snap.id, snap.storagePrefix(it), 0)) }
                        } ?: emptyList()
                    null -> emptyList()
                }
            // Captured with the payload: only the files this save drops are deleted after it.
            val dropped = detachedSnap
            updateContent { it.copy(isSaving = true, saveErrorRes = null) }
            // From here a pick still being read lands nowhere, and the attached photo is the one this save sends.
            snaps.beginCommit()
            viewModelScope.launch {
                // Popping the entry must not abort a write the server may have accepted (#852).
                withContext(NonCancellable) {
                    runCatchingCancellable {
                        writes.saveMoment(
                            MomentSavePayload.edit(
                                momentId,
                                state.draft.name,
                                state.draft.description,
                                state.draft.happenedAt,
                                snapPayload,
                            ),
                        )
                    }.fold(
                        onSuccess = {
                            snaps.markCommitted()
                            if (detachedSnap == dropped) detachedSnap = null
                            effectsOut.emit(EditMomentEffect.Saved)
                            // Best-effort, after the pop: the row is gone, the orphan sweep is the backstop.
                            dropped?.let { snapFiles.deleteFiles(storagePrefix = it.storagePath) }
                        },
                        onFailure = { failure ->
                            Log.e(TAG, "save_moment failed", failure)
                            // Uploaded files stay for the retry; dismiss is the cleanup (A-3).
                            snaps.abortCommit()
                            updateContent { it.copy(isSaving = false, saveErrorRes = momentWriteErrorRes(failure.toDataError())) }
                        },
                    )
                }
            }
        }

        private inline fun updateContent(crossinline block: (EditMomentUiState.Content) -> EditMomentUiState.Content) =
            _uiState.update { if (it is EditMomentUiState.Content) block(it) else it }

        private companion object {
            /** The same front-end clamp as Capture (M58 D3). */
            const val NAME_LIMIT = 40
        }
    }

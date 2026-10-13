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
import dagger.hilt.android.lifecycle.HiltViewModel
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
    ) : ViewModel() {
        private val effectsOut = UiEffects<EditMomentEffect>(viewModelScope)
        val effects: Flow<EditMomentEffect> = effectsOut.flow

        private val snaps = SnapUploadCoordinator(repo = snapFiles)
        private val _uiState = MutableStateFlow<EditMomentUiState>(EditMomentUiState.Loading)
        val uiState: StateFlow<EditMomentUiState> = _uiState.asStateFlow()

        private var loadedMomentId: String? = null
        private var detachedSnap: FormSnap.Existing? = null

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

        fun onDraftChange(draft: PebbleDraft) {
            if (isSaving) return
            updateContent { it.copy(draft = draft.copy(name = draft.name.take(NAME_LIMIT)), saveErrorRes = null) }
        }

        fun onPhotoPicked(uri: Uri) {
            if (isSaving) return
            val id = userId ?: return
            viewModelScope.launch {
                runCatchingCancellable { snaps.attach(media.process(uri), id) }
                    .onFailure { Log.e(TAG, "photo pick processing failed", it) }
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

        fun dismiss() {
            if (content?.isSaving == true) return
            viewModelScope.launch {
                userId?.let { snaps.cancelAndCleanup(it) }
                effectsOut.emit(EditMomentEffect.Dismissed)
            }
        }

        fun save() {
            val state = content ?: return
            if (!state.canSave) return
            if (snaps.isUploading) {
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
                            if (detachedSnap == dropped) detachedSnap = null
                            effectsOut.emit(EditMomentEffect.Saved)
                            // Best-effort, after the pop: the row is gone, the orphan sweep is the backstop.
                            dropped?.let { snapFiles.deleteFiles(storagePrefix = it.storagePath) }
                        },
                        onFailure = { failure ->
                            Log.e(TAG, "save_moment failed", failure)
                            // Uploaded files stay for the retry; dismiss is the cleanup (A-3).
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

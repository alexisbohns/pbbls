package app.pbbls.android.features.path

import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.core.model.FormSnap
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.PebbleDraft
import app.pbbls.android.core.model.PebbleSnapPayload
import app.pbbls.android.core.model.renderHeightDp
import app.pbbls.android.features.karma.KarmaNotificationService
import app.pbbls.android.features.karma.KarmaReason
import app.pbbls.android.features.pebblemedia.SnapUploadCoordinator
import app.pbbls.android.services.AchievementsServicing
import app.pbbls.android.services.ComposeResult
import app.pbbls.android.services.PebbleDetailServicing
import app.pbbls.android.services.PebbleWriteServicing
import app.pbbls.android.services.SnapWriteRepositing
import app.pbbls.android.services.SupabaseServicing
import app.pbbls.android.ui.UiEffects
import app.pbbls.android.ui.runCatchingCancellable
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

private const val TAG = "edit-pebble"

sealed interface EditPebbleUiState {
    data object Loading : EditPebbleUiState

    data object Error : EditPebbleUiState

    data class Content(
        val draft: PebbleDraft,
        val selectedGlyph: Glyph?,
        val renderSvg: String?,
        /** The view resolves the stroke colour from this — it needs the theme. */
        val emotionId: String?,
        val renderHeightDp: Int,
        val snap: FormSnap? = null,
        val isSaving: Boolean = false,
        @StringRes val saveErrorRes: Int? = null,
        val isRemovingExistingSnap: Boolean = false,
    ) : EditPebbleUiState
}

sealed interface EditPebbleEffect {
    data object Saved : EditPebbleEffect

    data object Dismissed : EditPebbleEffect
}

/**
 * State holder for the edit cover (#849).
 *
 * The write bug here is the same family as its siblings' but a step milder:
 * `save()` ran in `rememberCoroutineScope`, so leaving between the response and
 * `onSaved()` meant the server had the edit and the Path never reloaded — a
 * stale timeline until the next cold start rather than an orphan row. The
 * response handling runs in `withContext(NonCancellable)` all the same, because
 * the karma notify and the achievement check hang off it and neither is
 * repeated.
 *
 * `emotionId` and `renderHeightDp` leave as data rather than as resolved
 * colours: the stroke hex depends on the active colour scheme, which is the
 * view's to know. The old screen resolved it inside the load, which is why the
 * load had to read a CompositionLocal.
 */
@HiltViewModel
class EditPebbleViewModel
    @Inject
    constructor(
        private val detailService: PebbleDetailServicing,
        private val writeService: PebbleWriteServicing,
        private val karma: KarmaNotificationService,
        private val achievements: AchievementsServicing,
        private val supabase: SupabaseServicing,
        snapRepo: SnapWriteRepositing,
        private val media: ComposerMedia,
    ) : ViewModel() {
        private val effectsOut = UiEffects<EditPebbleEffect>(viewModelScope)
        val effects: Flow<EditPebbleEffect> = effectsOut.flow

        private val snaps = SnapUploadCoordinator(repo = snapRepo)

        private val _uiState = MutableStateFlow<EditPebbleUiState>(EditPebbleUiState.Loading)
        val uiState: StateFlow<EditPebbleUiState> = _uiState.asStateFlow()

        private var loadedPebbleId: String? = null

        private val userId: String?
            get() = supabase.session?.user?.id

        private val content: EditPebbleUiState.Content?
            get() = _uiState.value as? EditPebbleUiState.Content

        init {
            viewModelScope.launch {
                snapshotFlow { snaps.formSnap }.collect { snap ->
                    _uiState.update { if (it is EditPebbleUiState.Content) it.copy(snap = snap) else it }
                }
            }
        }

        /**
         * Load the pebble, unless it is the one already loaded.
         *
         * The guard is what keeps a rotation safe: it re-runs the screen's
         * `LaunchedEffect`, which must not throw away edits in progress, while
         * opening a *different* pebble must start clean. Since #852 this is its
         * own nav entry, so a different pebble is a different entry — but the
         * rotation case is unchanged and still needs the guard.
         */
        fun start(pebbleId: String) {
            if (loadedPebbleId == pebbleId) return
            loadedPebbleId = pebbleId
            snaps.reset()
            load(pebbleId)
        }

        fun retry() = loadedPebbleId?.let { load(it) }

        private fun load(pebbleId: String) {
            _uiState.value = EditPebbleUiState.Loading
            viewModelScope.launch {
                runCatchingCancellable { detailService.load(pebbleId) }
                    .fold(
                        onSuccess = { detail ->
                            _uiState.value =
                                EditPebbleUiState.Content(
                                    draft = PebbleDraft.from(detail),
                                    selectedGlyph = detail.glyph,
                                    renderSvg = detail.renderSvg,
                                    emotionId = detail.emotion.id,
                                    renderHeightDp = detail.valence.sizeGroup.renderHeightDp,
                                )
                            // iOS seeds only the first saved snap (at most one
                            // photo — M42 D1).
                            snaps.seedExisting(
                                detail.sortedSnaps.firstOrNull()?.let {
                                    FormSnap.Existing(id = it.id, storagePath = it.storagePath)
                                },
                            )
                        },
                        onFailure = {
                            Log.e(TAG, "edit pebble load failed", it)
                            _uiState.value = EditPebbleUiState.Error
                        },
                    )
            }
        }

        // MARK: - Form

        fun onDraftChange(draft: PebbleDraft) =
            _uiState.update {
                if (it !is EditPebbleUiState.Content) {
                    it
                } else {
                    // Ports iOS `.onChange(of: draft.glyphId) { if nil { selectedGlyph = nil } }`.
                    it.copy(draft = draft, selectedGlyph = if (draft.glyphId == null) null else it.selectedGlyph)
                }
            }

        fun onGlyphPicked(glyph: Glyph?) = _uiState.update { if (it is EditPebbleUiState.Content) it.copy(selectedGlyph = glyph) else it }

        /** Wired into [PebbleForm]'s soul picker — an inline soul creation fires the check (#852). */
        fun onAchievementCheck() = achievements.fireCheck()

        fun onPhotoPicked(uri: Uri) {
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
            val id = userId ?: return
            viewModelScope.launch { snaps.removePending(id) }
        }

        /**
         * Removing a *saved* photo is a server write (`delete_pebble_media`),
         * unlike removing a pending upload — so it has its own spinner and its
         * own error.
         */
        fun onRemoveExistingSnap() {
            updateContent { it.copy(isRemovingExistingSnap = true) }
            viewModelScope.launch {
                runCatchingCancellable { snaps.removeExisting() }
                    .onFailure {
                        Log.e(TAG, "delete_pebble_media failed", it)
                        updateContent { state -> state.copy(saveErrorRes = R.string.photo_remove_error) }
                    }
                updateContent { it.copy(isRemovingExistingSnap = false) }
            }
        }

        // MARK: - Leaving

        fun dismiss() {
            if (content?.isSaving == true) return
            viewModelScope.launch {
                userId?.let { snaps.cancelAndCleanup(it) }
                effectsOut.emit(EditPebbleEffect.Dismissed)
            }
        }

        // MARK: - Save

        fun save() {
            val state = content ?: return
            if (!state.draft.isValid || state.isSaving) return
            // Snap gates (M42): distinct copy per state, checked before the request.
            if (snaps.isUploading) {
                updateContent { it.copy(saveErrorRes = R.string.pebble_save_error_photo_uploading) }
                return
            }
            if (snaps.hasFailed) {
                updateContent { it.copy(saveErrorRes = R.string.pebble_save_error_photo_failed) }
                return
            }
            val pebbleId = loadedPebbleId ?: return
            updateContent { it.copy(isSaving = true, saveErrorRes = null) }

            viewModelScope.launch {
                val id = userId
                // Always-echo contract (M42 D5): existing echoes verbatim, a fresh
                // upload sends its pair, no snap sends [] (which deletes server-side).
                val snapPayload =
                    when (val formSnap = snaps.formSnap) {
                        is FormSnap.Existing -> listOf(PebbleSnapPayload(formSnap.id, formSnap.storagePath, 0))
                        is FormSnap.Pending ->
                            snaps.pendingSnapForPayload()?.let { snap ->
                                id?.let { listOf(PebbleSnapPayload(snap.id, snap.storagePrefix(it), 0)) }
                            } ?: emptyList()
                        null -> emptyList()
                    }
                // The write itself is inside NonCancellable, not just the
                // response handling (#852). Before these screens were nav
                // entries they lived on Path's entry, so closing the cover never
                // cancelled this scope. An entry is disposed when it is popped,
                // which cancels `viewModelScope` and would abort a request the
                // server may already have accepted — mirroring SoulFormViewModel,
                // which has always wrapped the whole section.
                withContext(NonCancellable) {
                    val result = writeService.update(pebbleId, state.draft, snapPayload)
                    when (result) {
                        is ComposeResult.Success -> {
                            updateContent { it.copy(renderSvg = result.response.renderSvg ?: it.renderSvg) }
                            karma.notifyEarned(result.response.karmaDelta ?: 0, KarmaReason.PEBBLE_ENRICHED)
                            // An edit can change the pebble's emotion, newly
                            // qualifying an emotion_first badge.
                            achievements.fireCheck()
                            effectsOut.emit(EditPebbleEffect.Saved)
                        }

                        is ComposeResult.SoftSuccess -> {
                            achievements.fireCheck()
                            effectsOut.emit(EditPebbleEffect.Saved)
                        }

                        is ComposeResult.Failure -> {
                            updateContent { it.copy(isSaving = false, saveErrorRes = result.messageRes) }
                            id?.let { snaps.handleSaveFailure(it) }
                        }
                    }
                }
            }
        }

        private inline fun updateContent(crossinline block: (EditPebbleUiState.Content) -> EditPebbleUiState.Content) =
            _uiState.update { if (it is EditPebbleUiState.Content) block(it) else it }
    }

package app.pbbls.android.features.path.create

import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.features.karma.KarmaNotificationService
import app.pbbls.android.features.karma.KarmaReason
import app.pbbls.android.features.path.ComposerMedia
import app.pbbls.android.features.path.models.KnownDraftIds
import app.pbbls.android.features.path.models.PebbleDraft
import app.pbbls.android.features.path.models.PebbleDraftPayload
import app.pbbls.android.features.path.models.PebbleSnapPayload
import app.pbbls.android.features.path.models.isSavableAsDraft
import app.pbbls.android.features.path.models.toDraft
import app.pbbls.android.features.pebblemedia.SnapUploadCoordinator
import app.pbbls.android.features.pebblemedia.models.FormSnap
import app.pbbls.android.services.AchievementsServicing
import app.pbbls.android.services.ComposeResult
import app.pbbls.android.services.ComposerDraftCoordinator
import app.pbbls.android.services.ComposerSnapshotStoring
import app.pbbls.android.services.PebbleDraftsServicing
import app.pbbls.android.services.PebbleWriteServicing
import app.pbbls.android.services.ReferenceDataServicing
import app.pbbls.android.services.SnapWriteRepositing
import app.pbbls.android.services.SupabaseServicing
import app.pbbls.android.ui.UiEffects
import app.pbbls.android.ui.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "create-pebble"

/** Long enough that typing does not thrash SharedPreferences (iOS parity). */
private const val AUTOSAVE_DEBOUNCE_MS = 800L

data class CreatePebbleUiState(
    val draft: PebbleDraft = PebbleDraft(),
    /** The picked glyph object, for rendering; `draft.glyphId` is what saves. */
    val selectedGlyph: Glyph? = null,
    val snap: FormSnap? = null,
    val isSaving: Boolean = false,
    val isSavingDraft: Boolean = false,
    @StringRes val saveErrorRes: Int? = null,
    val isRestorePromptPresented: Boolean = false,
) {
    /** Either write blocks the other, and both show the same spinner. */
    val isBusy: Boolean
        get() = isSaving || isSavingDraft
}

sealed interface CreatePebbleEffect {
    data class Created(
        val pebbleId: String,
    ) : CreatePebbleEffect

    data object DraftSaved : CreatePebbleEffect

    data object Cancelled : CreatePebbleEffect
}

/**
 * State holder for the all-at-once composer (#849) — the form reached by
 * long-pressing "New pebble" (M58 D1).
 *
 * **Two bugs, both the record flow's, because this screen is its sibling.**
 * `save()` ran in `rememberCoroutineScope`, so leaving the cover after the
 * request left the device but before `consumeDraftAfterPublish` ran produced an
 * orphan draft beside a real pebble. And `saveAsDraft()` could be cancelled
 * between the upsert and `autosave.clear()`, leaving a stale local snapshot
 * that would offer to restore work already saved to the server. Both sections
 * are now `withContext(NonCancellable)` inside [viewModelScope].
 *
 * **It uses [ComposerDraftCoordinator], which it did not before.** This screen
 * hand-rolled the same policy — `serverDraftId`, `restorableSnapshot`,
 * `hasCheckedSnapshot`, a bare `ComposerAutosave`, its own `verifyGlyph` and
 * its own consume-after-publish — while `RecordFlowViewModel` drove the
 * extracted, tested collaborator. That was two implementations of one
 * behaviour, and the hand-rolled one is where the cancellation holes were.
 * Adopting the coordinator is the consolidation, not a new abstraction: every
 * method used here already existed and is covered by its own tests.
 *
 * Activity-scoped, like its siblings — the cover is a conditionally-composed
 * child of `PathScreen`, so [start] and the reset in [finish] are the explicit
 * lifecycle until #852.
 */
@HiltViewModel
class CreatePebbleViewModel
    @Inject
    constructor(
        private val writeService: PebbleWriteServicing,
        private val refs: ReferenceDataServicing,
        private val karma: KarmaNotificationService,
        private val achievements: AchievementsServicing,
        private val supabase: SupabaseServicing,
        draftsService: PebbleDraftsServicing,
        snapshots: ComposerSnapshotStoring,
        snapRepo: SnapWriteRepositing,
        private val media: ComposerMedia,
    ) : ViewModel() {
        private val effectsOut = UiEffects<CreatePebbleEffect>(viewModelScope)
        val effects: Flow<CreatePebbleEffect> = effectsOut.flow

        private val drafts = ComposerDraftCoordinator(draftsService, snapshots)

        /** Form-scoped (M42 D6), now surviving rotation with the ViewModel. */
        private val snaps = SnapUploadCoordinator(repo = snapRepo)

        private val _uiState = MutableStateFlow(CreatePebbleUiState())
        val uiState: StateFlow<CreatePebbleUiState> = _uiState.asStateFlow()

        private val userId: String?
            get() = supabase.session?.user?.id

        private val knownIds: KnownDraftIds
            get() =
                KnownDraftIds(
                    soulIds = refs.souls.map { it.id }.toSet(),
                    collectionIds = refs.collections.map { it.id }.toSet(),
                )

        init {
            viewModelScope.launch {
                snapshotFlow { snaps.formSnap }.collect { snap -> _uiState.update { it.copy(snap = snap) } }
            }
            startAutosave()
        }

        /**
         * Called on open and again when reference data lands. No guard of its
         * own: the coordinator returns null until `refs.hasLoaded` and then
         * decides exactly once (#647).
         *
         * Takes the draft's id rather than the record (#852): a navigation key
         * can only carry an id, so the coordinator does the by-id fetch itself.
         */
        fun start(resumeDraftId: String?) {
            viewModelScope.launch {
                val decision = drafts.hydrate(resumeDraftId, refs.hasLoaded)
                _uiState.update { it.copy(isRestorePromptPresented = drafts.isRestorePromptPresented) }
                if (decision is ComposerDraftCoordinator.Decision.Resume) {
                    _uiState.update { it.copy(draft = decision.payload.toDraft(knownIds)) }
                    decision.payload.existingSnap?.let { snaps.seedExisting(it) }
                    _uiState.value.draft.glyphId
                        ?.let { verifyGlyph(it) }
                } else if (decision is ComposerDraftCoordinator.Decision.Failed) {
                    // No form state to seed — surface the failure through the same
                    // banner a failed publish uses (there is no other error slot
                    // on this state) rather than leaving the form silently blank.
                    _uiState.update { it.copy(saveErrorRes = decision.messageRes) }
                }
            }
        }

        // MARK: - Form

        fun onDraftChange(draft: PebbleDraft) =
            _uiState.update {
                // Ports iOS `.onChange(of: draft.glyphId) { if nil { selectedGlyph = nil } }`.
                it.copy(draft = draft, selectedGlyph = if (draft.glyphId == null) null else it.selectedGlyph)
            }

        fun onGlyphPicked(glyph: Glyph?) = _uiState.update { it.copy(selectedGlyph = glyph) }

        fun onPhotoPicked(uri: Uri) {
            val id = userId ?: return
            viewModelScope.launch {
                runCatchingCancellable { snaps.attach(media.process(uri), id) }
                    // iOS parity: a failed pick/decode logs and drops silently.
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

        // MARK: - Restore prompt

        fun acceptRestore() {
            _uiState.update { it.copy(isRestorePromptPresented = false) }
            drafts.takeRestorableSnapshot()?.let { snapshot ->
                _uiState.update { it.copy(draft = snapshot.toDraft(knownIds)) }
                snapshot.glyphId?.let { id -> viewModelScope.launch { verifyGlyph(id) } }
            }
        }

        fun discardRestore() {
            _uiState.update { it.copy(isRestorePromptPresented = false) }
            drafts.discardSnapshot()
        }

        /**
         * Drop a glyph the user can no longer use (design D7). Clears the
         * rendered glyph as well as the id — they are two fields describing one
         * choice, and leaving the object would keep drawing a glyph the save
         * will not carry.
         */
        private suspend fun verifyGlyph(glyphId: String) {
            val id = userId ?: return
            if (drafts.verifyGlyph(glyphId, id) == ComposerDraftCoordinator.GlyphVerdict.Unusable) {
                Log.i(TAG, "resumed draft referenced an unusable glyph — dropping it")
                _uiState.update { it.copy(draft = it.draft.copy(glyphId = null), selectedGlyph = null) }
            }
        }

        // MARK: - Autosave

        private fun startAutosave() {
            viewModelScope.launch {
                combine(
                    _uiState.map { it.draft }.distinctUntilChanged(),
                    snapshotFlow { snaps.formSnap },
                ) { draft, snap -> PebbleDraftPayload.from(draft, snap, userId) }
                    .distinctUntilChanged()
                    .collectLatest { payload ->
                        // Held off while the restore prompt is up so the pending
                        // answer is not overwritten before it is given.
                        if (drafts.isRestorePromptPresented || payload.isEmpty) return@collectLatest
                        drafts.stage(payload)
                        delay(AUTOSAVE_DEBOUNCE_MS)
                        drafts.flush()
                    }
            }
        }

        // MARK: - Leaving

        fun cancel() {
            if (_uiState.value.isBusy) return
            viewModelScope.launch {
                userId?.let { snaps.cancelAndCleanup(it) }
                finish()
                effectsOut.emit(CreatePebbleEffect.Cancelled)
            }
        }

        /**
         * Intentional "save as draft". Deliberately does NOT run
         * `snaps.cancelAndCleanup` — that would delete from Storage the very
         * object the draft references (design D3).
         */
        fun saveAsDraft() {
            if (_uiState.value.isBusy) return
            val id = userId
            if (id == null) {
                _uiState.update { it.copy(saveErrorRes = R.string.pebble_save_error_generic) }
                return
            }
            _uiState.update { it.copy(isSavingDraft = true, saveErrorRes = null) }
            viewModelScope.launch {
                // The upsert and the local-snapshot clear are one step: stopping
                // between them leaves a snapshot that would offer to restore work
                // the server already has.
                withContext(NonCancellable) {
                    val payload = PebbleDraftPayload.from(_uiState.value.draft, snaps.formSnap, id)
                    val error = drafts.saveAsDraft(payload, id)
                    if (error != null) {
                        _uiState.update { it.copy(isSavingDraft = false, saveErrorRes = error) }
                    } else {
                        finish()
                        effectsOut.emit(CreatePebbleEffect.DraftSaved)
                    }
                }
            }
        }

        // MARK: - Publish

        fun save() {
            val state = _uiState.value
            if (!state.draft.isValid || state.isBusy) return
            // Snap gates (M42): distinct copy per state, checked before the request.
            if (snaps.isUploading) {
                _uiState.update { it.copy(saveErrorRes = R.string.pebble_save_error_photo_uploading) }
                return
            }
            if (snaps.hasFailed) {
                _uiState.update { it.copy(saveErrorRes = R.string.pebble_save_error_photo_failed) }
                return
            }
            _uiState.update { it.copy(isSaving = true, saveErrorRes = null) }

            viewModelScope.launch {
                val id = userId
                val snapPayload =
                    snaps.pendingSnapForPayload()?.let { snap ->
                        id?.let { listOf(PebbleSnapPayload(snap.id, snap.storagePrefix(it), 0)) }
                    }
                // The write is inside NonCancellable too, not just the
                // reconciliation below (#852). Before this screen was a nav entry
                // it lived on Path's entry, so closing the cover never cancelled
                // this scope; an entry is disposed when popped, which cancels
                // `viewModelScope` and would abort a create the server may
                // already have accepted.
                //
                // Past the call the server has decided, and reconciling the
                // client with that decision — consuming the draft above all —
                // cannot be interrupted, or a published pebble keeps a draft
                // beside it.
                withContext(NonCancellable) {
                    val result = writeService.create(_uiState.value.draft, snapPayload)
                    when (result) {
                        is ComposeResult.Success -> {
                            karma.notifyEarned(result.response.karmaDelta ?: 0, KarmaReason.PEBBLE_CREATED)
                            achievements.fireCheck()
                            drafts.consumeAfterPublish()
                            finish()
                            effectsOut.emit(CreatePebbleEffect.Created(result.response.pebbleId))
                        }

                        is ComposeResult.SoftSuccess -> {
                            // Soft success still inserted the pebble, so counts
                            // changed and the draft is just as spent.
                            achievements.fireCheck()
                            drafts.consumeAfterPublish()
                            finish()
                            effectsOut.emit(CreatePebbleEffect.Created(result.pebbleId))
                        }

                        is ComposeResult.Failure -> {
                            _uiState.update { it.copy(isSaving = false, saveErrorRes = result.messageRes) }
                            id?.let { snaps.handleSaveFailure(it) }
                        }
                    }
                }
            }
        }

        /**
         * Clear for the next presentation. Explicit because the ViewModel is
         * activity-scoped: without it the next long-press opens onto the pebble
         * just published, and the coordinator's decide-once guard would skip
         * hydration for the rest of the session.
         */
        private fun finish() {
            drafts.reset()
            snaps.reset()
            _uiState.value = CreatePebbleUiState()
        }

        fun isSavableAsDraft(): Boolean = _uiState.value.draft.isSavableAsDraft(snaps.formSnap, userId)
    }

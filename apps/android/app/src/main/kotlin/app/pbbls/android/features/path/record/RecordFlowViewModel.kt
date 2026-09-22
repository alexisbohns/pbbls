package app.pbbls.android.features.path.record

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
import app.pbbls.android.core.data.AchievementsServicing
import app.pbbls.android.core.data.ComposeResult
import app.pbbls.android.core.data.ComposerDraftCoordinator
import app.pbbls.android.core.data.ComposerSnapshotStoring
import app.pbbls.android.core.data.KarmaNotificationService
import app.pbbls.android.core.data.PebbleDraftsServicing
import app.pbbls.android.core.data.PebbleWriteServicing
import app.pbbls.android.core.data.ReferenceDataServicing
import app.pbbls.android.core.data.SnapUploadCoordinator
import app.pbbls.android.core.data.SnapWriteRepositing
import app.pbbls.android.core.data.SupabaseServicing
import app.pbbls.android.core.data.TapHaptic
import app.pbbls.android.core.model.ComposePebbleResponse
import app.pbbls.android.core.model.FormSnap
import app.pbbls.android.core.model.KarmaReason
import app.pbbls.android.core.model.KnownDraftIds
import app.pbbls.android.core.model.PebbleDraftPayload
import app.pbbls.android.core.model.PebbleSnapPayload
import app.pbbls.android.core.model.isSavableAsDraft
import app.pbbls.android.features.path.ComposerMedia
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

private const val TAG = "record-flow"

/** Long enough that typing does not thrash SharedPreferences (iOS parity). */
internal const val AUTOSAVE_DEBOUNCE_MS = 800L

/**
 * One-shot things the flow asks the screen to do (#849).
 *
 * [Haptic] is here rather than called directly because a `ViewModel` has no
 * `View` to buzz, and `rememberTapHaptics()` needs one. Routing it out as an
 * effect keeps the M58 D4 rule intact — every interaction still goes through
 * [RecordFlowModel], and every method there still buzzes — while moving the
 * one part that genuinely needs a view back to the view.
 */
sealed interface RecordFlowEffect {
    data class Haptic(
        val flavor: TapHaptic,
    ) : RecordFlowEffect

    /** Published: reload the Path behind the still-visible success step (D10). */
    data class Published(
        val pebbleId: String,
    ) : RecordFlowEffect

    data object Dismiss : RecordFlowEffect

    data object DraftSaved : RecordFlowEffect
}

/**
 * The whole cover's state: the flow machine plus what the screen wraps around
 * it (#849).
 *
 * Not a `Loading`/`Error`/`Content` triple, and deliberately so — the composer
 * has nothing to load before it can render. The sealed-interface shape is for
 * screens that fetch; this one is a form, and its honest state is a record.
 */
data class RecordFlowUiState(
    val flow: RecordFlowState = RecordFlowState(),
    val snap: FormSnap? = null,
    val isRestorePromptPresented: Boolean = false,
    val isCloseConfirmPresented: Boolean = false,
    /** True when the `when` step's date came from the photo's EXIF (M58 D7). */
    val seededFromPhoto: Boolean = false,
    /** Non-null while the attached photo blocks publishing — the two rules the flow enforces. */
    @StringRes val snapBlockedMessageRes: Int? = null,
)

/**
 * The record flow's orchestration (#849) — the coordinators, the writes and the
 * draft lifecycle that `RecordFlowScreen` used to own in `rememberCoroutineScope`.
 *
 * **The bug this exists for.** `publish()` ran in the composition's scope, so
 * leaving the cover after the request left the device but before the response
 * came back cancelled the coroutine mid-handler: the server created the pebble,
 * the client never ran `consumeAfterPublish`, `onPublished` never fired — an
 * orphan draft beside a real pebble, and a Path that did not know about it.
 * Two changes fix it. The work runs in [viewModelScope], which a leaving
 * composition does not cancel; and the handler's "the server has accepted this"
 * section runs inside `withContext(NonCancellable)`, so even clearing the
 * ViewModel cannot interrupt the draft cleanup half-done.
 *
 * [RecordFlowModel] is kept, not absorbed: it is the flow's state machine, its
 * 27 tests are the spec for gating, resume and the haptic-per-interaction rule
 * (M58 D4), and none of that is orchestration. What changed is its storage —
 * a `StateFlow<RecordFlowState>` this class persists and exposes.
 *
 * **Entry-scoped (#852).** The flow is its own `PebblesKey.RecordFlow`
 * destination, and `RootScreen` decorates entries with
 * `rememberViewModelStoreNavEntryDecorator()`, so this ViewModel is created
 * with the entry and cleared when it is popped — verified on device by logging
 * one init/clear pair per visit with fresh identities. [startFlow] and
 * [resetForNext] survive as defence in depth rather than as the lifecycle: they
 * were load-bearing when the ViewModel outlived the cover it drove.
 */
@HiltViewModel
class RecordFlowViewModel
    @Inject
    constructor(
        private val savedState: SavedStateHandle,
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
        private val effectsOut = UiEffects<RecordFlowEffect>(viewModelScope)
        val effects: Flow<RecordFlowEffect> = effectsOut.flow

        /**
         * Every interaction's buzz leaves as an effect. No default and no no-op:
         * a defaulted haptic is exactly the silent failure [RecordFlowModel]'s
         * design exists to prevent.
         */
        private val model = RecordFlowModel(haptic = { effectsOut.emit(RecordFlowEffect.Haptic(it)) })

        private val drafts = ComposerDraftCoordinator(draftsService, snapshots)

        /**
         * Form-scoped (M42 D6): an in-flight upload must not outlive the form.
         * Scoped to the ViewModel rather than the composition, so it survives a
         * rotation mid-upload — which used to restart the whole attach.
         */
        private val snaps = SnapUploadCoordinator(repo = snapRepo)

        private val _uiState = MutableStateFlow(RecordFlowUiState())
        val uiState: StateFlow<RecordFlowUiState> = _uiState.asStateFlow()

        private val userId: String?
            get() = supabase.session?.user?.id

        /**
         * The step the process died on, read **once at construction**.
         *
         * It has to be captured here rather than looked up when the user accepts
         * the restore prompt: the step collector in [startAutosave] writes the
         * current step as soon as it starts, so by the time the prompt is
         * answered the handle holds `PHOTO` — the value of the blank flow that
         * is on screen behind the dialog, not the one being restored.
         *
         * Never the terminal step: the response that put the user there is gone,
         * so the success screen would render blank.
         */
        private var restoredStep: RecordStep? =
            savedState
                .get<String>(KEY_STEP)
                ?.let { name -> RecordStep.entries.firstOrNull { it.name == name } }
                ?.takeIf { it != RecordStep.SUCCESS }

        /**
         * Known soul and collection ids, for sanitizing a resumed payload.
         * Read off the reference cache at use time rather than recomputed per
         * recomposition, which is what the screen did.
         */
        private val knownIds: KnownDraftIds
            get() =
                KnownDraftIds(
                    soulIds = refs.souls.map { it.id }.toSet(),
                    collectionIds = refs.collections.map { it.id }.toSet(),
                )

        init {
            // The flow machine and the upload coordinator are the two state
            // sources; both fold into the one value the screen collects.
            viewModelScope.launch {
                model.state.collect { flow -> _uiState.update { it.copy(flow = flow) } }
            }
            viewModelScope.launch {
                snapshotFlow { Triple(snaps.formSnap, snaps.isUploading, snaps.hasFailed) }
                    .collect { (snap, isUploading, hasFailed) ->
                        // The photo step's Skip / Done label reads off the model, so
                        // the coordinator's state is mirrored onto it rather than the
                        // model owning media.
                        model.hasSnap = snap != null
                        _uiState.update {
                            it.copy(
                                snap = snap,
                                snapBlockedMessageRes =
                                    when {
                                        isUploading -> R.string.pebble_save_error_photo_uploading
                                        hasFailed -> R.string.pebble_save_error_photo_failed
                                        else -> null
                                    },
                            )
                        }
                    }
            }
            // The valence fan wobbles eighteen assets the first time it draws,
            // a visible hitch on the main thread. Two steps of runway is plenty,
            // and the caches are process-wide, so a second flow pays nothing.
            viewModelScope.launch { media.prewarmValence() }
            startAutosave()
        }

        // MARK: - Lifecycle

        /**
         * Called whenever the cover opens, and again when reference data lands.
         *
         * No guard of its own: `ComposerDraftCoordinator.hydrate` already returns
         * null until `refs.hasLoaded` and then decides exactly once, which is the
         * behaviour #647 put there. A guard here would latch on the first call —
         * the one that happens *before* refs load — and the draft would never
         * hydrate at all.
         *
         * Takes the draft's id rather than the record (#852): a navigation key
         * can only carry an id, so the coordinator does the by-id fetch itself.
         */
        fun startFlow(resumeDraftId: String?) = hydrate(resumeDraftId)

        /**
         * Clears the machine after a terminal step, before the entry pops.
         *
         * This was load-bearing when the ViewModel was activity-scoped and
         * outlived the cover: without it, reopening the composer showed the
         * pebble published five minutes ago. Since #852 the entry takes the
         * ViewModel with it, so this is belt to that braces — kept because the
         * publish and discard paths call it before the pop, not after.
         */
        private fun resetForNext() {
            model.reset()
            drafts.reset()
            restoredStep = null
            snaps.reset()
            savedState.remove<String>(KEY_STEP)
            _uiState.value = RecordFlowUiState()
        }

        /**
         * Hydrate-or-offer-restore, gated on `refs.hasLoaded` (#647): hydrating
         * before the souls / collections caches arrive would sanitize against
         * empty sets and silently drop every soul and collection.
         */
        private fun hydrate(resumeDraftId: String?) {
            viewModelScope.launch {
                val decision = drafts.hydrate(resumeDraftId, refs.hasLoaded)
                _uiState.update {
                    it.copy(isRestorePromptPresented = drafts.isRestorePromptPresented)
                }
                if (decision is ComposerDraftCoordinator.Decision.Resume) {
                    model.resume(decision.payload, knownIds)
                    decision.payload.existingSnap?.let {
                        snaps.seedExisting(it)
                        model.hasSnap = true
                    }
                    model.draft.glyphId?.let { verifyGlyph(it) }
                    restoreStep()
                } else if (decision is ComposerDraftCoordinator.Decision.Failed) {
                    // No composer state to seed — surface the failure through the
                    // same banner a failed publish uses rather than leaving the
                    // flow silently blank.
                    model.fail(decision.messageRes)
                }
            }
        }

        /**
         * Accept the crash-restore prompt (M47): the local snapshot becomes the
         * draft, and the flow lands where the user actually was rather than at
         * `firstGap()`.
         *
         * That last part is what [SavedStateHandle] carries here, and the only
         * thing it carries. The draft itself already survives process death
         * through `ComposerSnapshotStore`'s autosave, and a second restore path
         * for the same value would be two mechanisms racing to answer one
         * question — so the handle holds the step, which the snapshot does not.
         */
        fun acceptRestore() {
            _uiState.update { it.copy(isRestorePromptPresented = false) }
            drafts.takeRestorableSnapshot()?.let { snapshot ->
                model.resume(snapshot, knownIds)
                model.draft.glyphId?.let { id -> viewModelScope.launch { verifyGlyph(id) } }
                restoreStep()
            }
        }

        fun discardRestore() {
            _uiState.update { it.copy(isRestorePromptPresented = false) }
            drafts.discardSnapshot()
        }

        private fun restoreStep() {
            restoredStep?.let { model.goTo(it) }
            // One presentation only: a later reopen starts at the top.
            restoredStep = null
        }

        private suspend fun verifyGlyph(glyphId: String) {
            val id = userId ?: return
            when (drafts.verifyGlyph(glyphId, id)) {
                ComposerDraftCoordinator.GlyphVerdict.Unusable -> model.clearGlyph()
                ComposerDraftCoordinator.GlyphVerdict.Usable,
                ComposerDraftCoordinator.GlyphVerdict.Unknown,
                -> Unit
            }
        }

        // MARK: - Autosave

        /**
         * Local crash-insurance autosave, debounced. Held off while the restore
         * prompt is up so the pending answer is not overwritten before it is
         * given.
         */
        private fun startAutosave() {
            // Where the user is, kept separately from what they have written —
            // see [acceptRestore] for why the handle carries only this.
            viewModelScope.launch {
                model.state
                    .map { it.step }
                    .distinctUntilChanged()
                    .collect { savedState[KEY_STEP] = it.name }
            }
            viewModelScope.launch {
                // Two sources, two mechanisms: the machine is a StateFlow, the
                // upload coordinator is Compose state. `snapshotFlow` sees only
                // the second, so combining them is not stylistic — keying the
                // autosave off `model.draft` inside a snapshotFlow would observe
                // nothing and silently stop autosaving on every keystroke.
                //
                // Keyed on the PAYLOAD and nothing else, which is what the
                // screen's `LaunchedEffect(currentPayload, …)` was. Widening it
                // to the whole flow state resurrects the snapshot after a
                // publish: `succeed()` changes the step, the collector re-runs
                // with an unchanged payload, and it writes back what
                // `consumeAfterPublish` had just cleared.
                combine(
                    model.state.map { it.draft }.distinctUntilChanged(),
                    snapshotFlow { snaps.formSnap },
                ) { draft, snap -> PebbleDraftPayload.from(draft, snap, userId) }
                    .distinctUntilChanged()
                    .collectLatest { payload ->
                        // A published pebble is not a draft. Belt to the keying
                        // above, since this is the case that costs a user a
                        // phantom draft beside a real pebble.
                        if (model.published != null) return@collectLatest
                        // Held off while the restore prompt is up so the pending
                        // answer is not overwritten before it is given.
                        if (drafts.isRestorePromptPresented || payload.isEmpty) return@collectLatest
                        drafts.stage(payload)
                        // collectLatest cancels this on the next keystroke, which
                        // is what makes the delay a debounce rather than a queue.
                        delay(AUTOSAVE_DEBOUNCE_MS)
                        drafts.flush()
                    }
            }
        }

        // MARK: - Interactions routed to the machine

        /** The flow's state machine. Every tap goes through it (M58 D4). */
        fun machine(): RecordFlowModel = model

        /** Wired into the souls step's inline soul creation (#852). */
        fun onAchievementCheck() = achievements.fireCheck()

        // MARK: - Photo

        /**
         * A picked photo: EXIF first, then the re-encode.
         *
         * `ImagePipeline` re-encodes with `Bitmap.compress`, which writes no
         * metadata at all, so the capture date is gone by the time bytes exist
         * (D7).
         */
        fun onPhotoPicked(uri: Uri) {
            val id = userId ?: return
            viewModelScope.launch {
                runCatchingCancellable {
                    val picked = media.captureDate(uri)
                    _uiState.update { it.copy(seededFromPhoto = picked != null) }
                    model.applyCaptureDate(picked)
                    snaps.attach(media.process(uri), id)
                }.onFailure {
                    // iOS parity: a failed pick/decode logs and drops silently.
                    Log.e(TAG, "photo pick processing failed", it)
                }
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

        // MARK: - Leaving

        /** ✕ only asks when there is something to keep (D9). */
        fun onCloseRequested() {
            if (model.isPublishing) return
            if (model.draft.isSavableAsDraft(snaps.formSnap, userId)) {
                _uiState.update { it.copy(isCloseConfirmPresented = true) }
            } else {
                discardAndClose()
            }
        }

        fun onKeepGoing() = _uiState.update { it.copy(isCloseConfirmPresented = false) }

        fun onDiscard() {
            _uiState.update { it.copy(isCloseConfirmPresented = false) }
            discardAndClose()
        }

        private fun discardAndClose() {
            viewModelScope.launch {
                userId?.let { snaps.cancelAndCleanup(it) }
                drafts.discardSnapshot()
                resetForNext()
                effectsOut.emit(RecordFlowEffect.Dismiss)
            }
        }

        /** The success step's button, and system back from the terminal step. */
        fun onExit() {
            resetForNext()
            effectsOut.emit(RecordFlowEffect.Dismiss)
        }

        fun onSaveAsDraft() {
            _uiState.update { it.copy(isCloseConfirmPresented = false) }
            val id = userId
            if (id == null) {
                Log.e(TAG, "save draft: no current user id")
                model.fail(R.string.record_signed_out_error)
                return
            }
            viewModelScope.launch {
                // Deliberately no snap cleanup: the draft references that snap.
                val error = drafts.saveAsDraft(PebbleDraftPayload.from(model.draft, snaps.formSnap, id), id)
                if (error != null) {
                    model.fail(error)
                } else {
                    resetForNext()
                    effectsOut.emit(RecordFlowEffect.DraftSaved)
                }
            }
        }

        /**
         * System back mirrors the chrome: unwind an open glyph swap, then step
         * backwards, and only ask to leave from the first step.
         *
         * [unwindGlyphPicker] is a callback rather than state on this class
         * because the open/closed glyph swap belongs to the picker's own
         * `rememberGlyphPickerState`. It is passed in rather than checked by the
         * caller so the *order* stays here and stays testable: unwinding has to
         * come after the publishing and success guards, or back would close the
         * swap sheet mid-publish.
         */
        fun onSystemBack(unwindGlyphPicker: () -> Boolean = { false }) {
            when {
                model.isPublishing -> Unit
                model.step == RecordStep.SUCCESS -> onExit()
                unwindGlyphPicker() -> Unit
                model.step.previous != null -> model.back()
                else -> onCloseRequested()
            }
        }

        // MARK: - Publish

        fun publish() {
            val id = userId
            if (id == null) {
                Log.e(TAG, "publish: no current user id")
                model.fail(R.string.record_signed_out_error)
                return
            }
            // Snap gates (M42): distinct copy per state, checked before the request.
            if (snaps.isUploading) {
                model.fail(R.string.pebble_save_error_photo_uploading)
                return
            }
            if (snaps.hasFailed) {
                model.fail(R.string.pebble_save_error_photo_failed)
                return
            }
            viewModelScope.launch {
                model.beginPublish()
                val snapPayload =
                    snaps.pendingSnapForPayload()?.let { snap ->
                        listOf(PebbleSnapPayload(snap.id, snap.storagePrefix(id), 0))
                    }
                val result = writeService.create(model.draft, snapPayload)

                // Past this line the server has already decided. Everything that
                // reconciles the client with that decision — consuming the draft
                // above all — runs uninterruptibly, because a cancellation here is
                // exactly the orphan-draft bug: pebble created, draft never
                // consumed, Path never told.
                withContext(NonCancellable) {
                    when (result) {
                        is ComposeResult.Success -> {
                            // The success step shows the amount, so the capsule
                            // would be redundant (D10).
                            karma.notifyEarned(
                                result.response.karmaDelta ?: 0,
                                KarmaReason.PEBBLE_CREATED,
                                presentsCapsule = false,
                            )
                            achievements.fireCheck()
                            drafts.consumeAfterPublish()
                            model.succeed(result.response)
                            effectsOut.emit(RecordFlowEffect.Published(result.response.pebbleId))
                        }

                        is ComposeResult.SoftSuccess -> {
                            // The pebble exists but the compose step failed, so
                            // there is no render and no karma amount to show — the
                            // success step degrades to the name alone rather than
                            // blocking (D10).
                            achievements.fireCheck()
                            drafts.consumeAfterPublish()
                            model.succeed(ComposePebbleResponse(pebbleId = result.pebbleId))
                            effectsOut.emit(RecordFlowEffect.Published(result.pebbleId))
                        }

                        is ComposeResult.Failure -> {
                            // A hard failure never reaches the success step: the
                            // flow stays on privacy so ✕ → Save as draft is still a
                            // way out.
                            model.fail(result.messageRes)
                            snaps.handleSaveFailure(id)
                        }
                    }
                }
            }
        }

        private companion object {
            const val KEY_STEP = "record-flow-step"
        }
    }

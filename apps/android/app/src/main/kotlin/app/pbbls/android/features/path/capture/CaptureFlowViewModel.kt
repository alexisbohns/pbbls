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
import app.pbbls.android.core.data.CameraPhotos
import app.pbbls.android.core.data.HapticsPreference
import app.pbbls.android.core.data.MomentWriteServicing
import app.pbbls.android.core.data.ProcessedImage
import app.pbbls.android.core.data.SnapUploadCoordinator
import app.pbbls.android.core.data.SnapWriteRepositing
import app.pbbls.android.core.data.SupabaseServicing
import app.pbbls.android.core.data.TapHaptic
import app.pbbls.android.core.data.toDataError
import app.pbbls.android.core.model.AttachedSnap
import app.pbbls.android.core.model.AttachedSnap.UploadState.UPLOADED
import app.pbbls.android.core.model.FormSnap
import app.pbbls.android.core.model.MomentSavePayload
import app.pbbls.android.core.model.PebbleSnapPayload
import app.pbbls.android.core.ui.momentWriteErrorRes
import app.pbbls.android.features.path.ComposerMedia
import app.pbbls.android.features.path.PathFocusRequests
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
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
import java.time.temporal.ChronoUnit
import javax.inject.Inject

private const val TAG = "capture-flow"
private const val KEY_STEP = "capture-step"
private const val KEY_NAME = "capture-name"
private const val KEY_WORDS = "capture-words"
private const val KEY_WHEN = "capture-when-epoch"
private const val KEY_WHEN_EDITED = "capture-when-edited"
private const val KEY_WHEN_SEEDED = "capture-when-seeded"
private const val KEY_SNAP_ID = "capture-snap-id"

/** The camera's file while the camera app is in front: the process may die there, so it is saved state. */
private const val KEY_SHOT = "capture-shot"

/** How long a pick stays visible on the photo screen before the page takes over (D3). */
private const val PHOTO_ADVANCE_BEAT_MS = 400L

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
    /** Non-null while the attached photo blocks saving: still uploading, or failed. */
    @StringRes val snapBlockedMessageRes: Int? = null,
    val isCloseConfirmPresented: Boolean = false,
    /** The close dialog offers Save moment only when the moment is savable now (R2). */
    val closeOffersSave: Boolean = false,
    /**
     * Home's New pebble (#1383 D7): the page's one save is "Save & make it a pebble", which
     * chains into Complete, and "Just save" is not offered (iOS `chainsComplete`).
     */
    val chainsComplete: Boolean = false,
    /** Quick moment (D13): opened on the page, with no way back to the photo screen. */
    val startsAtMomentPage: Boolean = false,
    /** The onboarding hand-off's reason slug (#1510 D9); Part 8 reads it for the name field's prompt. */
    val promptReason: String? = null,
    /** A pick is being read or handed over, over a photo or not: the library tile shows its busy state. */
    val isPicking: Boolean = false,
    /** Whether a camera app answers; false hides the camera button (D3). Read once, at construction. */
    val cameraAvailable: Boolean = false,
) {
    /**
     * Both saves on the moment page: a trimmed name or an uploaded photo, and
     * nothing blocking. A photo still uploading, or failed, does not count.
     */
    val canSaveNow: Boolean get() = flow.canSave && snapBlockedMessageRes == null
}

/**
 * Capture: a photo and a moment page, saved with `save_moment` (A-2). The page is an unsaved preview
 * (D2): nothing is written until a save button. What `SavedStateHandle` holds (step, name, words, the
 * date and where it came from, an uploaded photo's id) is Android's draft store: it covers process
 * death, where iOS needs its own autosaver (A-5).
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
        private val haptics: HapticsPreference,
        private val photos: CameraPhotos,
    ) : ViewModel() {
        private val effectsOut = UiEffects<CaptureFlowEffect>(viewModelScope)
        val effects: Flow<CaptureFlowEffect> = effectsOut.flow

        /** Pebbles' haptics switch (#1242 D23): the screen asks it at each buzz, so Settings applies at once. */
        val hapticsOn: Boolean get() = haptics.isOn

        /**
         * Whether a step was saved before this ViewModel existed. Read here, before the collector in `init`
         * starts: that collector writes the step at once, and would make every start look restored.
         */
        private val hasRestoredStep: Boolean = savedState.contains(KEY_STEP)

        /** The key's step is applied once, and never over a restored one. */
        private var hasAppliedKeyStep = false

        /** An uploaded photo's id from before the process died: its files are in Storage, its bytes are gone. */
        private val restoredSnap: FormSnap? =
            savedState.get<String>(KEY_SNAP_ID)?.let {
                FormSnap.Pending(AttachedSnap(id = it, localThumb = ByteArray(0), state = AttachedSnap.UploadState.UPLOADED))
            }

        private val model =
            CaptureFlowModel(
                haptic = { effectsOut.emit(CaptureFlowEffect.Haptic(it)) },
                initial =
                    CaptureFlowState(
                        draft =
                            CaptureDraft(
                                name = savedState.get<String>(KEY_NAME).orEmpty(),
                                description = savedState.get<String>(KEY_WORDS).orEmpty(),
                                happenedAt =
                                    savedState.get<Long>(KEY_WHEN)?.let {
                                        OffsetDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneId.systemDefault())
                                    } ?: OffsetDateTime.now(),
                            ),
                        // A saved step from an older build resolves to the photo; a restored photo means the page.
                        step =
                            if (restoredSnap != null) {
                                CaptureStep.MOMENT
                            } else {
                                savedState.get<String>(KEY_STEP)?.let { saved -> CaptureStep.entries.firstOrNull { it.name == saved } }
                                    ?: CaptureStep.PHOTO
                            },
                        hasSnap = restoredSnap != null,
                        hasEditedDate = savedState.get<Boolean>(KEY_WHEN_EDITED) ?: false,
                        seededFromPhoto = savedState.get<Boolean>(KEY_WHEN_SEEDED) ?: false,
                    ),
            )

        /** Form-scoped (M42 D6), held by the ViewModel so a rotation mid-upload keeps the upload. */
        private val snaps = SnapUploadCoordinator(repo = snapRepo, initialSnap = restoredSnap)

        /**
         * Whether the draft is written to [savedState]. Off from a discard or a save on: a write after
         * either must never bring a finished draft back (the entry pops, and this is the second guard).
         */
        private var persistsDraft = true

        private val _uiState =
            MutableStateFlow(
                CaptureFlowUiState(flow = model.state.value, snap = restoredSnap, cameraAvailable = photos.isAvailable()),
            )
        val uiState: StateFlow<CaptureFlowUiState> = _uiState.asStateFlow()

        private val userId: String?
            get() = supabase.session?.user?.id

        /** The pick or retry in flight, held so a discard can stop it before cleaning up. */
        private var photoJob: Job? = null

        /** The beat between a new pick and the page; held so leaving the step, or a discard, cancels it. */
        private var advanceJob: Job? = null

        /**
         * A pick from the moment it is chosen until its upload settles. Before the
         * coordinator has a snap for it, this is the only sign of it: it counts as
         * something to keep (R2) and blocks the save like an upload (iOS).
         */
        private var pickCount = 0
            set(value) {
                field = value
                _uiState.update { it.copy(isPicking = isPicking, snapBlockedMessageRes = blockedBySnap()) }
            }

        private val isPicking: Boolean get() = pickCount > 0

        init {
            viewModelScope.launch {
                model.state.collect { flow ->
                    // Leaving the photo step by any road (Just write, back, a discard) cancels a pending advance.
                    if (flow.step != CaptureStep.PHOTO || flow.isFrozen) advanceJob?.cancel()
                    if (persistsDraft) {
                        savedState[KEY_STEP] = flow.step.name
                        savedState[KEY_NAME] = flow.draft.name
                        savedState[KEY_WORDS] = flow.draft.description
                        savedState[KEY_WHEN] = flow.draft.happenedAt.toEpochSecond()
                        savedState[KEY_WHEN_EDITED] = flow.hasEditedDate
                        savedState[KEY_WHEN_SEEDED] = flow.seededFromPhoto
                    }
                    _uiState.update { it.copy(flow = flow) }
                }
            }
            // Seeded before the first collection: a restored photo is not a new pick, and never advances.
            var lastSnapId: String? = (restoredSnap as? FormSnap.Pending)?.snap?.id
            viewModelScope.launch {
                snapshotFlow { snaps.formSnap }.collect { snap ->
                    // Once saved, `snaps.reset()` empties the coordinator; the screen keeps showing what it
                    // showed through the exit animation instead of flipping to "Add a photo".
                    if (model.state.value.isSaved) return@collect
                    model.hasSnap = snap != null
                    // Advance on a new snap id only: an upload state change (FAILED to UPLOADED after a retry) keeps
                    // the same id, and must not bounce someone who came back through Change photo.
                    val id = (snap as? FormSnap.Pending)?.snap?.id
                    if (id != null && id != lastSnapId && model.step == CaptureStep.PHOTO) scheduleAdvance()
                    lastSnapId = id
                    // Only a photo already in Storage is worth keeping across a kill (iOS `savedSnap`):
                    // a pick still uploading is picked again after a restore.
                    if (persistsDraft) savedState[KEY_SNAP_ID] = ((snap as? FormSnap.Pending)?.snap?.takeIf { it.state == UPLOADED })?.id
                    _uiState.update { it.copy(snap = snap, snapBlockedMessageRes = blockedBySnap()) }
                }
            }
        }

        /** Every tap goes through the machine (M58 D4). */
        fun machine(): CaptureFlowModel = model

        /**
         * The key's arguments, from the screen before its first frame. Idempotent. A quick moment
         * (D13) lands on the page, unless a step was restored: then it stays where it was.
         */
        fun start(
            chainsComplete: Boolean,
            startsAtMomentPage: Boolean = false,
            promptReason: String? = null,
        ) {
            if (startsAtMomentPage && !hasRestoredStep && !hasAppliedKeyStep) model.goTo(CaptureStep.MOMENT)
            hasAppliedKeyStep = true
            // The flow too, now: its collector runs a beat later, and the first frame must already be the page.
            _uiState.update {
                it.copy(
                    flow = model.state.value,
                    chainsComplete = chainsComplete,
                    startsAtMomentPage = startsAtMomentPage,
                    promptReason = promptReason,
                )
            }
        }

        fun onPhotoPicked(uri: Uri) {
            if (model.isFrozen) return
            attachPhoto {
                // EXIF first: the re-encode writes no metadata (M58 D7).
                model.applyCaptureDate(media.captureDate(uri))
                media.process(uri)
            }
        }

        /**
         * The camera button: the shot's content URI to launch the camera with, or null (frozen, no camera app, a
         * shot already out, or no room for a file). The path is saved state, because the camera is another process
         * and this one may die while it is in front.
         */
        fun onCameraRequested(): Uri? {
            if (model.isFrozen || !photos.isAvailable() || savedState.contains(KEY_SHOT)) return null
            val shot =
                runCatchingCancellable { photos.newShot() }.getOrElse {
                    Log.e(TAG, "no file for a camera shot", it)
                    return null
                }
            val uri =
                runCatchingCancellable { photos.uriFor(shot) }.getOrElse {
                    Log.e(TAG, "no uri for a camera shot", it)
                    photos.delete(shot)
                    return null
                }
            savedState[KEY_SHOT] = shot
            return uri
        }

        /** The system had no camera app after all (`ActivityNotFoundException`): the file is dropped, nothing breaks. */
        fun onCameraLaunchFailed() {
            Log.w(TAG, "the camera could not be launched")
            dropShot()
        }

        /**
         * The camera's answer, possibly to a new ViewModel after a process death: the shot comes from saved state.
         * The shutter is when (until the user says otherwise), and the file is deleted in every branch.
         */
        fun onCameraResult(taken: Boolean) {
            val shot = savedState.get<String>(KEY_SHOT) ?: return
            savedState.remove<String>(KEY_SHOT)
            if (!taken || model.isFrozen) {
                photos.delete(shot)
                return
            }
            attachPhoto(cleanup = { photos.delete(shot) }) {
                model.applyCaptureDate(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS))
                photos.process(shot)
            }
        }

        /** Back in front with a shot that never got its answer: it is dropped, so the camera is never locked out. */
        fun onResumed() {
            if (savedState.contains(KEY_SHOT)) Log.w(TAG, "a camera shot came back without a result; dropped")
            dropShot()
        }

        private fun dropShot() {
            savedState.get<String>(KEY_SHOT)?.let(photos::delete)
            savedState.remove<String>(KEY_SHOT)
        }

        /**
         * One pick, from the library or the camera: [prepare] reads it, and only once it has bytes does it cancel the pick
         * before it and [SnapUploadCoordinator.attach] it, which deletes the photo it replaces. A pick that cannot be
         * read therefore leaves the photo that was there exactly as it was, uploading or not. [cleanup] always runs.
         * ATOMIC, so a job cancelled before it starts still reaches its `finally`, and a camera shot is never left behind.
         */
        private fun attachPhoto(
            cleanup: () -> Unit = {},
            prepare: suspend () -> ProcessedImage,
        ) {
            val id =
                userId ?: run {
                    Log.e(TAG, "photo pick dropped: no current user")
                    cleanup()
                    return
                }
            val previous = photoJob
            pickCount += 1
            photoJob =
                viewModelScope.launch(start = CoroutineStart.ATOMIC) {
                    try {
                        runCatchingCancellable {
                            val processed = prepare()
                            previous?.cancelAndJoin()
                            snaps.attach(processed, id)
                        }.onFailure { Log.e(TAG, "photo pick processing failed", it) }
                    } finally {
                        pickCount -= 1
                        cleanup()
                    }
                }
        }

        private fun scheduleAdvance() {
            advanceJob?.cancel()
            advanceJob =
                viewModelScope.launch {
                    delay(PHOTO_ADVANCE_BEAT_MS)
                    // The machine guards frozen itself; the step check is for a user who already left.
                    if (model.step == CaptureStep.PHOTO) model.advance()
                }
        }

        fun onRetryPhoto() {
            if (model.isFrozen) return
            val id = userId ?: return
            photoJob = viewModelScope.launch { snaps.retryCurrent(id) }
        }

        fun onRemovePhoto() {
            if (model.isFrozen) return
            val id = userId ?: return
            viewModelScope.launch { snaps.removePending(id) }
        }

        fun save(thenComplete: Boolean) {
            if (model.isFrozen) return
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
            model.beginSave(thenComplete)
            viewModelScope.launch {
                // Popping the entry must not abort a write the server may have accepted (#852).
                withContext(NonCancellable) {
                    runCatchingCancellable {
                        writes.saveMoment(
                            MomentSavePayload.capture(draft.name, draft.description, draft.happenedAt, snap),
                        )
                    }.fold(
                        onSuccess = { momentId ->
                            // The moment exists: the draft is finished, whatever happens to this entry next.
                            clearDraft()
                            model.saved()
                            // The files now belong to the moment: forget them without deleting.
                            snaps.reset()
                            // Path shows the moment's week when it is next on screen (R14).
                            focusRequests.request(momentId)
                            effectsOut.emit(
                                if (thenComplete) CaptureFlowEffect.SavedThenComplete(momentId) else CaptureFlowEffect.Saved,
                            )
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
                isPicking || snaps.isUploading -> R.string.pebble_save_error_photo_uploading
                snaps.hasFailed -> R.string.pebble_save_error_photo_failed
                else -> null
            }

        /**
         * Asks only when there is something to lose (R2): "Keep this moment?"
         * with Save moment (when savable and the photo is not blocking),
         * Discard and Keep going.
         */
        fun onCloseRequested() {
            if (model.isFrozen) return
            val state = model.state.value
            if (state.hasInput || isPicking) {
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
            // Before anything suspends: no collector may write the draft back after this point.
            advanceJob?.cancel()
            dropShot()
            clearDraft()
            viewModelScope.launch {
                // An upload finishing after the cleanup would leave its files behind.
                photoJob?.cancelAndJoin()
                userId?.let { snaps.cancelAndCleanup(it) }
                effectsOut.emit(CaptureFlowEffect.Dismiss)
            }
        }

        /** Stops persisting and forgets what was kept, so a finished or discarded draft cannot come back. */
        private fun clearDraft() {
            persistsDraft = false
            listOf(
                KEY_STEP,
                KEY_NAME,
                KEY_WORDS,
                KEY_WHEN,
                KEY_WHEN_EDITED,
                KEY_WHEN_SEEDED,
                KEY_SNAP_ID,
            ).forEach { savedState.remove<Any>(it) }
        }

        /** Back walks the steps; on the first one reachable it is ✕ (R2). */
        fun onSystemBack() {
            when {
                model.isFrozen -> Unit
                model.step.previous != null && !_uiState.value.startsAtMomentPage -> model.back()
                else -> onCloseRequested()
            }
        }
    }

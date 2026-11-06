package app.pbbls.android.core.data

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.pbbls.android.core.model.AttachedSnap
import app.pbbls.android.core.model.FormSnap
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID

private const val TAG = "snap-coordinator"

/**
 * Single source of truth for the photo attached to an in-progress pebble form —
 * ports iOS `SnapUploadCoordinator.swift` verbatim (design D3). Owns [formSnap]
 * (the form-layer state), [processedForRetry] (re-encoded bytes kept for a
 * user-tapped retry), and every transition: attach → upload (one auto-retry
 * after [retryDelayMillis], then FAILED) → retry → remove → cancel.
 *
 * Form-scoped, not app-level (design D6): Create/Edit screens construct one
 * with `remember` so an in-flight upload dies with the form. All Storage
 * side-effects are explicitly awaited — no sleep-based race in
 * [cancelAndCleanup]. [onLog] is injectable because the failure paths run in
 * JVM unit tests, where `android.util.Log` is unavailable (SnapURLCache rule).
 */
class SnapUploadCoordinator(
    private val repo: SnapWriteRepositing,
    initialSnap: FormSnap? = null,
    private val retryDelayMillis: Long = 2_000L,
    private val onLog: (String, Exception?) -> Unit = { message, e -> Log.w(TAG, message, e) },
) {
    var formSnap: FormSnap? by mutableStateOf(initialSnap)
        private set

    /** Where the form stands (iOS `lifecycle`). Plain fields: nothing in the UI reads them. */
    private enum class Lifecycle { OPEN, COMMITTING, COMMITTED, CLOSED }

    private var lifecycle = Lifecycle.OPEN

    /** The pending snap a save sent: no cleanup ever deletes it. */
    private var committedSnapId: String? = null

    /** Bumped at every pick's tap: a pick applies only while it is still the latest (iOS `pickGeneration`). */
    private var pickGeneration = 0

    private val isCommitLocked: Boolean get() = lifecycle == Lifecycle.COMMITTING || lifecycle == Lifecycle.COMMITTED

    /**
     * Processed bytes kept around so a FAILED → user-tapped retry doesn't
     * re-decode + re-encode the pick. Cleared whenever [formSnap] clears.
     * Not UI-read, so a plain field.
     */
    var processedForRetry: ProcessedImage? = null
        private set

    /** True while the pending snap is still uploading. */
    val isUploading: Boolean
        get() = pendingState() == AttachedSnap.UploadState.UPLOADING

    /** True when the pending snap's last upload attempt failed. */
    val hasFailed: Boolean
        get() = pendingState() == AttachedSnap.UploadState.FAILED

    /** True while Save should be blocked (mid-upload, or failed and needing user action). */
    val isBlocking: Boolean
        get() = isUploading || hasFailed

    /**
     * The pending [AttachedSnap] only when fully uploaded — suitable for a
     * save payload. Null for none/existing/in-flight/failed (existing snaps
     * are encoded directly from [formSnap] by the payload builder).
     */
    fun pendingSnapForPayload(): AttachedSnap? =
        (formSnap as? FormSnap.Pending)
            ?.snap
            ?.takeIf { it.state == AttachedSnap.UploadState.UPLOADED }

    /**
     * Set the initial snap after construction — the edit screen has to wait
     * for its detail load before it knows whether the pebble has a snap.
     */
    fun seedExisting(snap: FormSnap?) {
        formSnap = snap
    }

    /**
     * Back to an empty form, with no Storage cleanup (#849).
     *
     * For reusing the coordinator within one presentation. It mattered more when
     * the owning ViewModel was activity-scoped and outlived the composer; since
     * #852 the entry clears it. It is NOT the
     * way to abandon an upload — that is [cancelAndCleanup], which also fires the
     * compensating delete. Calling this with a pending snap would leak the
     * uploaded bytes, so the caller must have finished or cancelled first. It keeps the lifecycle: a form whose
     * save committed stays closed to a pick that lands late.
     */
    fun reset() {
        formSnap = null
        processedForRetry = null
    }

    /** Called at a pick's tap, before it is read, and handed to [attach]: a later tap makes this one stale. */
    fun nextPickGeneration(): Int {
        pickGeneration += 1
        return pickGeneration
    }

    /**
     * Called right before the save's write. Until [markCommitted] or [abortCommit] a pick is refused and the
     * attached photo can be neither removed, replaced nor cancelled away, so the save sends exactly what it read.
     */
    fun beginCommit() {
        if (lifecycle != Lifecycle.OPEN) return
        lifecycle = Lifecycle.COMMITTING
        committedSnapId = (formSnap as? FormSnap.Pending)?.snap?.id
    }

    /** The save succeeded: the photo belongs to a saved moment; a pick that lands late neither replaces nor deletes it. */
    fun markCommitted() {
        if (lifecycle != Lifecycle.COMMITTING) committedSnapId = (formSnap as? FormSnap.Pending)?.snap?.id
        lifecycle = Lifecycle.COMMITTED
    }

    /** The save failed: the form is open again and keeps its photo for the retry. */
    fun abortCommit() {
        if (lifecycle != Lifecycle.COMMITTING) return
        lifecycle = Lifecycle.OPEN
        committedSnapId = null
    }

    private fun accepts(generation: Int): Boolean =
        when {
            lifecycle != Lifecycle.OPEN -> {
                onLog("pick ignored: form is $lifecycle", null)
                false
            }
            generation != pickGeneration -> {
                onLog("pick ignored: superseded by a later pick", null)
                false
            }
            else -> true
        }

    /**
     * A processed pick enters the machine: pending-uploading, then [performUpload]. [generation] is the one taken
     * at the pick's tap; a pick applies only if the form is open and no later pick has been tapped, and otherwise
     * touches nothing: it attaches, replaces and deletes nothing (iOS `accepts`).
     *
     * A pending snap it replaces takes its Storage files with it (iOS `ingest`): nobody else holds them. A saved
     * (existing) one is untouched, since it is the host's to detach. The replaced snap's own upload, cancelled by
     * the caller, may still land after that delete, and so may this one if it is removed or replaced while in
     * flight: when the upload ends (or is cancelled) and the form no longer holds this snap, its files are
     * deleted again, uncancellable (iOS `deleteFilesUncancelled`), unless a save sent it ([committedSnapId]).
     */
    suspend fun attach(
        processed: ProcessedImage,
        userId: String,
        generation: Int = nextPickGeneration(),
    ) {
        if (!accepts(generation)) return
        val replaced = (formSnap as? FormSnap.Pending)?.snap
        val snapId = UUID.randomUUID().toString().lowercase()
        formSnap =
            FormSnap.Pending(
                AttachedSnap(
                    id = snapId,
                    localThumb = processed.thumb,
                    state = AttachedSnap.UploadState.UPLOADING,
                ),
            )
        processedForRetry = processed
        if (replaced != null) {
            onLog("replacing pending snap ${replaced.id}", null)
            withContext(NonCancellable) { repo.deleteFiles(snapId = replaced.id, userId = userId) }
        }
        try {
            performUpload(processed = processed, snapId = snapId, userId = userId)
        } finally {
            if ((formSnap as? FormSnap.Pending)?.snap?.id != snapId && snapId != committedSnapId) {
                onLog("late upload: deleting orphaned $snapId", null)
                withContext(NonCancellable) { repo.deleteFiles(snapId = snapId, userId = userId) }
            }
        }
    }

    /**
     * Re-run the upload after a user tap on the chip's retry button. Only
     * valid while pending with retained bytes; no-op otherwise.
     */
    suspend fun retryCurrent(userId: String) {
        val pending = formSnap as? FormSnap.Pending ?: return
        val processed = processedForRetry ?: return
        formSnap = FormSnap.Pending(pending.snap.copy(state = AttachedSnap.UploadState.UPLOADING))
        performUpload(processed = processed, snapId = pending.snap.id, userId = userId)
    }

    /**
     * Remove the current pending snap and fire the compensating Storage
     * delete. Safe in any upload state; no-op when not pending.
     */
    suspend fun removePending(userId: String) {
        if (isCommitLocked) return
        val pending = formSnap as? FormSnap.Pending ?: return
        formSnap = null
        processedForRetry = null
        repo.deleteFiles(snapId = pending.snap.id, userId = userId)
    }

    /**
     * Detach the saved photo from the form. Nothing is written here: Edit
     * moment's `save_moment` drops the row through its whole-set `snaps`, which
     * is also where "a moment needs a name or a photo" is enforced, and the
     * caller deletes the returned snap's files once that save succeeds (R6).
     */
    fun detachExisting(): FormSnap.Existing? {
        if (isCommitLocked) return null
        val existing = formSnap as? FormSnap.Existing ?: return null
        formSnap = null
        return existing
    }

    /**
     * Cancel-button path: capture the pending id, clear state, and await the
     * compensating delete. Storage failures are logged inside the repo, never
     * thrown — Cancel must not be un-cancellable.
     */
    suspend fun cancelAndCleanup(userId: String) {
        // A save that started owns the photo.
        if (isCommitLocked) return
        // A pick still being read must not land on a closed form.
        lifecycle = Lifecycle.CLOSED
        val pending = formSnap as? FormSnap.Pending
        formSnap = null
        processedForRetry = null
        if (pending != null && pending.snap.id != committedSnapId) {
            repo.deleteFiles(snapId = pending.snap.id, userId = userId)
        }
    }

    /**
     * Single upload path for [attach] and [retryCurrent]: on first failure,
     * wait [retryDelayMillis] and retry once; on second failure transition to
     * FAILED (bytes retained). State writes guard on snap-id match so a
     * removal mid-upload can't resurrect the chip.
     */
    private suspend fun performUpload(
        processed: ProcessedImage,
        snapId: String,
        userId: String,
    ) {
        try {
            repo.uploadProcessed(processed = processed, snapId = snapId, userId = userId)
            applyStateIfSnapMatches(snapId, AttachedSnap.UploadState.UPLOADED)
        } catch (first: Exception) {
            onLog("snap upload failed (first attempt)", first)
            delay(retryDelayMillis)
            try {
                repo.uploadProcessed(processed = processed, snapId = snapId, userId = userId)
                applyStateIfSnapMatches(snapId, AttachedSnap.UploadState.UPLOADED)
            } catch (second: Exception) {
                onLog("snap upload failed (retry)", second)
                applyStateIfSnapMatches(snapId, AttachedSnap.UploadState.FAILED)
            }
        }
    }

    private fun pendingState(): AttachedSnap.UploadState? = (formSnap as? FormSnap.Pending)?.snap?.state

    private fun applyStateIfSnapMatches(
        snapId: String,
        newState: AttachedSnap.UploadState,
    ) {
        val pending = formSnap as? FormSnap.Pending ?: return
        if (pending.snap.id != snapId) return
        formSnap = FormSnap.Pending(pending.snap.copy(state = newState))
    }
}

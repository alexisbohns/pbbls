package app.pbbls.android.features.pebblemedia

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.pbbls.android.features.pebblemedia.models.AttachedSnap
import app.pbbls.android.features.pebblemedia.models.FormSnap
import app.pbbls.android.services.SnapWriteRepositing
import kotlinx.coroutines.delay
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

    /** A processed pick enters the machine: pending-uploading, then [performUpload]. */
    suspend fun attach(
        processed: ProcessedImage,
        userId: String,
    ) {
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
        performUpload(processed = processed, snapId = snapId, userId = userId)
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
        val pending = formSnap as? FormSnap.Pending ?: return
        formSnap = null
        processedForRetry = null
        repo.deleteFiles(snapId = pending.snap.id, userId = userId)
    }

    /**
     * Remove the current existing snap: `delete_pebble_media` (throws to the
     * screen on failure — the form surfaces it), then fire-and-forget Storage
     * cleanup of the returned path. No-op when not existing.
     */
    suspend fun removeExisting() {
        val existing = formSnap as? FormSnap.Existing ?: return
        val storagePath = repo.deletePebbleMedia(snapId = existing.id)
        repo.deleteFiles(storagePrefix = storagePath)
        formSnap = null
    }

    /**
     * Cancel-button path: capture the pending id, clear state, and await the
     * compensating delete. Storage failures are logged inside the repo, never
     * thrown — Cancel must not be un-cancellable.
     */
    suspend fun cancelAndCleanup(userId: String) {
        val pending = formSnap as? FormSnap.Pending
        formSnap = null
        processedForRetry = null
        if (pending != null) {
            repo.deleteFiles(snapId = pending.snap.id, userId = userId)
        }
    }

    /**
     * Save (compose-pebble) failed with a pending snap attached: await the
     * compensating Storage delete but KEEP [formSnap] — the form stays open
     * so the user can retry. Use [cancelAndCleanup] to also dismiss.
     * (Verbatim iOS semantics — design D3.)
     */
    suspend fun handleSaveFailure(userId: String) {
        val pending = formSnap as? FormSnap.Pending ?: return
        repo.deleteFiles(snapId = pending.snap.id, userId = userId)
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

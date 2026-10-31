package app.pbbls.android.features.home.camera

import android.util.Log
import androidx.annotation.StringRes
import app.pbbls.android.R
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.AchievementsServicing
import app.pbbls.android.core.data.MomentWriteServicing
import app.pbbls.android.core.data.SnapUploadCoordinator
import app.pbbls.android.core.model.MomentSavePayload
import app.pbbls.android.core.model.PebbleSnapPayload
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

private const val TAG = "home-camera"

/**
 * What Home shows of the camera moment: the camera piece is off (0.6, D6) and "Saving your photo…"
 * reads while [isSaving]; [isUnavailable] is the "No camera yet" alert; [errorRes] a failure line.
 */
data class CameraMomentUiState(
    val isSaving: Boolean = false,
    val isUnavailable: Boolean = false,
    @StringRes val errorRes: Int? = null,
)

/**
 * Home's camera moment (#1383 D7; iOS `CameraMomentCoordinator`): the system camera takes one photo,
 * it uploads through a fresh [SnapUploadCoordinator] (Capture's pipeline), then one `save_moment`
 * records a nameless moment at the shutter time, floored to whole seconds. Nothing is saved when the
 * upload fails ("The photo didn't upload. Nothing was saved."); a refused save deletes the uploaded
 * files ("Couldn't save the moment. Try again."). Success fires the achievement check and hands
 * [onLanded] the id and the shutter time, for the snackbar.
 *
 * The order of events:
 * 1. [start] checks for a camera, makes the shot's file and returns its path for the launcher, or
 *    null: no camera (the alert), a save still running or a shot already out (double capture).
 * 2. [onResult] takes the camera's answer. A cancel deletes the file; a photo saves in [scope]
 *    under `NonCancellable`, since the upload and the write must finish once they have left.
 * 3. The shot's file is deleted in every branch: cancel, no session, a failed read, a failed upload,
 *    a refused save and a saved moment alike.
 *
 * The pending shot outlives a process death through [onPendingShot] (the owner's `SavedStateHandle`),
 * because the camera app is in front, and Android may reclaim this process, exactly while one is out.
 * [onResumed] drops a shot whose answer never came (results land before the resume), so the camera
 * is never locked out.
 */
class CameraMoment(
    private val scope: CoroutineScope,
    private val photos: CameraPhotos,
    private val writes: MomentWriteServicing,
    private val achievements: AchievementsServicing,
    private val userId: () -> String?,
    private val makeSnaps: () -> SnapUploadCoordinator,
    private val onLanded: (momentId: String, shutter: OffsetDateTime) -> Unit,
    initialPendingShot: String? = null,
    private val onPendingShot: (String?) -> Unit = {},
    private val now: () -> OffsetDateTime = OffsetDateTime::now,
) {
    private val _state = MutableStateFlow(CameraMomentUiState())
    val state: StateFlow<CameraMomentUiState> = _state.asStateFlow()

    private var pendingShot: String? = initialPendingShot
        set(value) {
            field = value
            onPendingShot(value)
        }

    /** The camera piece's tap: the shot's file path to launch the camera with, or null (see the class doc). */
    fun start(): String? {
        if (_state.value.isSaving || pendingShot != null) return null
        if (!photos.isAvailable()) {
            _state.update { it.copy(isUnavailable = true) }
            return null
        }
        val shot =
            runCatchingCancellable { photos.newShot() }.getOrElse { error ->
                Log.e(TAG, "no file for a camera shot", error)
                _state.update { it.copy(errorRes = R.string.home_quick_error) }
                return null
            }
        pendingShot = shot
        _state.update { it.copy(errorRes = null) }
        return shot
    }

    /** The system said no camera app after all (`ActivityNotFoundException`): the alert, and no file left. */
    fun onLaunchFailed() {
        pendingShot?.let(photos::delete)
        pendingShot = null
        _state.update { it.copy(isUnavailable = true) }
    }

    /** The camera's answer: [taken] false is a cancel. */
    fun onResult(taken: Boolean) {
        val shot = pendingShot ?: return
        pendingShot = null
        if (!taken) {
            photos.delete(shot)
            return
        }
        // Read when the photo comes back (iOS reads it in the picker's callback), whole seconds.
        val shutter = now().truncatedTo(ChronoUnit.SECONDS)
        _state.update { it.copy(isSaving = true, errorRes = null) }
        scope.launch {
            withContext(NonCancellable) {
                try {
                    save(shot, shutter)?.let { error -> _state.update { it.copy(errorRes = error) } }
                } finally {
                    photos.delete(shot)
                    _state.update { it.copy(isSaving = false) }
                }
            }
        }
    }

    /** Home came back to the front: a shot still out never got its answer, so it is dropped. */
    fun onResumed() {
        val stale = pendingShot ?: return
        Log.w(TAG, "a camera shot came back without a result; dropped")
        photos.delete(stale)
        pendingShot = null
    }

    fun dismissUnavailable() = _state.update { it.copy(isUnavailable = false) }

    fun dismissError() = _state.update { it.copy(errorRes = null) }

    /** The upload then the write; the failure line's string resource, or null once the moment is saved. */
    private suspend fun save(
        shot: String,
        shutter: OffsetDateTime,
    ): Int? {
        val user =
            userId() ?: run {
                Log.e(TAG, "camera moment: no session")
                return R.string.home_quick_error
            }
        val processed =
            runCatchingCancellable { photos.process(shot) }.getOrElse { error ->
                Log.e(TAG, "camera photo could not be read", error)
                return R.string.home_camera_upload_failed
            }
        val snaps = makeSnaps()
        snaps.attach(processed, user)
        val snap =
            snaps.pendingSnapForPayload() ?: run {
                Log.e(TAG, "camera upload did not finish; nothing saved")
                snaps.cancelAndCleanup(user)
                return R.string.home_camera_upload_failed
            }
        val payload =
            MomentSavePayload(
                happenedAt = shutter,
                snaps = listOf(PebbleSnapPayload(snap.id, snap.storagePrefix(user), 0)),
            )
        return runCatchingCancellable { writes.saveMoment(payload) }.fold(
            onSuccess = { momentId ->
                // The files are the moment's now: forget them without deleting.
                snaps.reset()
                achievements.fireCheck()
                onLanded(momentId, shutter)
                null
            },
            onFailure = { error ->
                Log.e(TAG, "camera save_moment failed", error)
                snaps.cancelAndCleanup(user)
                R.string.home_quick_error
            },
        )
    }
}

package app.pbbls.android.features.home

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.core.common.UiEffects
import app.pbbls.android.core.data.AchievementsServicing
import app.pbbls.android.core.data.MomentWriteServicing
import app.pbbls.android.core.data.SnapUploadCoordinator
import app.pbbls.android.core.data.SnapWriteRepositing
import app.pbbls.android.core.data.SupabaseServicing
import app.pbbls.android.features.home.camera.CameraMoment
import app.pbbls.android.features.home.camera.CameraPhotos
import app.pbbls.android.features.home.quick.QuickMoment
import app.pbbls.android.features.home.quick.QuickMomentExit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import java.time.OffsetDateTime
import javax.inject.Inject

/** What Home does once one of its own flows has written a moment (#1383 D7). */
sealed interface HomeMomentEffect {
    /** The quick moment saved: Home reloads, and opens Complete for [exit] [QuickMomentExit.OPEN]. */
    data class QuickSaved(
        val momentId: String,
        val exit: QuickMomentExit,
    ) : HomeMomentEffect

    /** The camera piece asked for a photo: the screen launches `TakePicture` into [uri]. */
    data class LaunchCamera(
        val uri: Uri,
    ) : HomeMomentEffect

    /** A camera moment saved: Home reloads, and the snackbar offers to name it. */
    data class CameraLanded(
        val momentId: String,
        val shutter: OffsetDateTime,
    ) : HomeMomentEffect
}

/** Where the shot out at the camera app waits across a process death. */
private const val KEY_CAMERA_SHOT = "home-camera-shot"

/**
 * The writes Home starts itself (#1383 D7): the quick moment's sheet, and the camera moment. Scoped to
 * Home's entry like [HomeViewModel], beside it rather than inside it: [HomeViewModel] owns Home's reads
 * and keeps its constructor to them, this one owns the two flows' state holders and runs their writes
 * in its `viewModelScope`, so a save outlives the sheet that started it.
 *
 * The sheet is not a navigation key (iOS presents it over Home, and Android draws it as a
 * `ModalBottomSheet` there), so there is no entry to scope a sheet-only ViewModel to: [quick] is a
 * plain holder, fresh each time the sheet opens; [camera] is the camera moment's ([CameraMoment]).
 * After any save, Home reloads what it shows through
 * [HomeViewModel.onMomentSaved]; the Path re-reads on its own resume (#852).
 */
@HiltViewModel
class HomeMomentsViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        writes: MomentWriteServicing,
        achievements: AchievementsServicing,
        supabase: SupabaseServicing,
        snapRepo: SnapWriteRepositing,
        private val photos: CameraPhotos,
    ) : ViewModel() {
        private val effectsOut = UiEffects<HomeMomentEffect>(viewModelScope)
        val effects: Flow<HomeMomentEffect> = effectsOut.flow

        val quick =
            QuickMoment(
                scope = viewModelScope,
                writes = writes,
                achievements = achievements,
                onSaved = { id, exit -> effectsOut.emit(HomeMomentEffect.QuickSaved(id, exit)) },
            )

        val camera =
            CameraMoment(
                scope = viewModelScope,
                photos = photos,
                writes = writes,
                achievements = achievements,
                userId = { supabase.session?.user?.id },
                // A fresh coordinator per shot (iOS `makeSnaps`), so one capture never leaks into the next.
                makeSnaps = { SnapUploadCoordinator(repo = snapRepo) },
                onLanded = { id, shutter -> effectsOut.emit(HomeMomentEffect.CameraLanded(id, shutter)) },
                initialPendingShot = savedState[KEY_CAMERA_SHOT],
                onPendingShot = { savedState[KEY_CAMERA_SHOT] = it },
            )

        /** The camera piece's tap: the launch, the "No camera yet" alert, or nothing while one is out or saving. */
        fun onCamera() {
            val shot = camera.start() ?: return
            effectsOut.emit(HomeMomentEffect.LaunchCamera(photos.uriFor(shot)))
        }

        fun onResumed() = camera.onResumed()
    }

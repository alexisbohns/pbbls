package app.pbbls.android.features.home

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.core.common.UiEffects
import app.pbbls.android.core.data.AchievementsServicing
import app.pbbls.android.core.data.CameraPhotos
import app.pbbls.android.core.data.MomentWriteServicing
import app.pbbls.android.core.data.OnboardingHandoff
import app.pbbls.android.core.data.ReferenceDataServicing
import app.pbbls.android.core.data.SnapUploadCoordinator
import app.pbbls.android.core.data.SnapWriteRepositing
import app.pbbls.android.core.data.SupabaseServicing
import app.pbbls.android.core.ui.onboardingPromptSlug
import app.pbbls.android.features.home.camera.CameraMoment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import javax.inject.Inject

/** What Home does once one of its own flows has written a moment (#1383 D7). */
sealed interface HomeMomentEffect {
    /**
     * The onboarding's hand-off: open the moment page, its NAME placeholder the prompt the reasons chose.
     * [promptReason] is a stable slug (`onboardingPromptSlug`), because it rides on a navigation key.
     */
    data class OpenMomentPage(
        val promptReason: String,
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
 * The writes Home starts itself (#1383 D7): the camera moment. Scoped to Home's entry like
 * [HomeViewModel], beside it rather than inside it: [HomeViewModel] owns Home's reads and keeps its
 * constructor to them, this one owns [camera]'s state holder and runs its write in its `viewModelScope`
 * ([CameraMoment]). After a save, Home reloads what it shows through [HomeViewModel.onMomentSaved];
 * the Path re-reads on its own resume (#852). The quick moment is not here any more: its tile opens the
 * moment page (record-flow D13).
 *
 * The onboarding's hand-off (#1510 D9) lands here: [onResumed] takes the flow's note once and asks Home
 * to open the moment page with the reasons' prompt.
 */
@HiltViewModel
class HomeMomentsViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        writes: MomentWriteServicing,
        achievements: AchievementsServicing,
        private val supabase: SupabaseServicing,
        snapRepo: SnapWriteRepositing,
        private val photos: CameraPhotos,
        private val handoff: OnboardingHandoff,
        private val reference: ReferenceDataServicing,
    ) : ViewModel() {
        private val effectsOut = UiEffects<HomeMomentEffect>(viewModelScope)
        val effects: Flow<HomeMomentEffect> = effectsOut.flow

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

        /**
         * The camera's resume, then the onboarding's note, once (#1510 D9): the moment page opens with
         * the prompt the first reason chose as its name placeholder (the default one when step 3 was
         * skipped), and the souls list is refreshed so the flow's new souls are in the Complete picker
         * straight away. Nobody is pre-attached: souls attach to pebbles, and `save_moment` has no way
         * to hold one.
         */
        fun onResumed() {
            camera.onResumed()
            val note = handoff.take(supabase.session?.user?.id) ?: return
            effectsOut.emit(HomeMomentEffect.OpenMomentPage(onboardingPromptSlug(note.reason)))
            viewModelScope.launch { reference.refreshSouls() }
        }
    }

package app.pbbls.android.features.home

import androidx.lifecycle.SavedStateHandle
import app.pbbls.android.R
import app.pbbls.android.core.data.OnboardingHandoff
import app.pbbls.android.core.data.OnboardingHandoffNote
import app.pbbls.android.core.model.OnboardingReason
import app.pbbls.android.testing.FakeAchievementsService
import app.pbbls.android.testing.FakeCameraPhotos
import app.pbbls.android.testing.FakeMomentWriteService
import app.pbbls.android.testing.FakeReferenceDataService
import app.pbbls.android.testing.FakeSnapWriteRepository
import app.pbbls.android.testing.FakeSupabaseService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.testSession
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** The onboarding's hand-off into Home's quick composer (#1510 D9). */
class HomeMomentsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val handoff = OnboardingHandoff()
    private val reference = FakeReferenceDataService()

    private fun viewModel(userId: String = "user-1") =
        HomeMomentsViewModel(
            savedState = SavedStateHandle(),
            writes = FakeMomentWriteService(),
            achievements = FakeAchievementsService(),
            supabase = FakeSupabaseService(session = testSession(userId), isInitializing = false),
            snapRepo = FakeSnapWriteRepository(),
            photos = FakeCameraPhotos(),
            handoff = handoff,
            reference = reference,
        )

    @Test
    fun `the first resume after the flow opens the composer with the prompt and refreshes the souls`() =
        runTest {
            handoff.leave(OnboardingHandoffNote(userId = "user-1", reason = OnboardingReason.GOOD_DAYS))
            val vm = viewModel()

            vm.onResumed()
            advanceUntilIdle()

            assertEquals(R.string.onboarding_handoff_prompt_good_days, requireNotNull(vm.quick.sheet.value).promptRes)
            assertEquals(1, reference.refreshSoulsCount)
        }

    @Test
    fun `the note is taken once, and a resume with no note opens nothing`() =
        runTest {
            handoff.leave(OnboardingHandoffNote(userId = "user-1", reason = null))
            val vm = viewModel()

            vm.onResumed()
            advanceUntilIdle()
            assertEquals(R.string.onboarding_handoff_prompt_default, requireNotNull(vm.quick.sheet.value).promptRes)

            vm.quick.dismiss()
            vm.onResumed()
            advanceUntilIdle()
            assertNull(vm.quick.sheet.value)
            assertEquals(1, reference.refreshSoulsCount)
        }

    @Test
    fun `a note left for another user is not taken, and is gone afterwards`() =
        runTest {
            handoff.leave(OnboardingHandoffNote(userId = "user-1", reason = OnboardingReason.GOOD_DAYS))

            val other = viewModel(userId = "user-2")
            other.onResumed()
            advanceUntilIdle()
            assertNull(other.quick.sheet.value)

            val owner = viewModel(userId = "user-1")
            owner.onResumed()
            advanceUntilIdle()
            assertNull(owner.quick.sheet.value)
        }
}

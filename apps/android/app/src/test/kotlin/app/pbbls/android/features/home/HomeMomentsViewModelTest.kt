package app.pbbls.android.features.home

import androidx.lifecycle.SavedStateHandle
import app.pbbls.android.core.data.OnboardingHandoff
import app.pbbls.android.core.data.OnboardingHandoffNote
import app.pbbls.android.core.model.OnboardingReason
import app.pbbls.android.core.ui.ONBOARDING_PROMPT_DEFAULT_SLUG
import app.pbbls.android.testing.FakeAchievementsService
import app.pbbls.android.testing.FakeCameraPhotos
import app.pbbls.android.testing.FakeMomentWriteService
import app.pbbls.android.testing.FakeReferenceDataService
import app.pbbls.android.testing.FakeSnapWriteRepository
import app.pbbls.android.testing.FakeSupabaseService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.recordEffects
import app.pbbls.android.testing.testSession
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    fun `the first resume after the flow opens the moment page with the reason's slug and refreshes the souls`() =
        runTest {
            handoff.leave(OnboardingHandoffNote(userId = "user-1", reason = OnboardingReason.GOOD_DAYS))
            val vm = viewModel()
            val effects = recordEffects(vm.effects)

            vm.onResumed()
            advanceUntilIdle()
            effects.stop()

            assertEquals(listOf<HomeMomentEffect>(HomeMomentEffect.OpenMomentPage("good-days")), effects.values)
            assertEquals(1, reference.refreshSoulsCount)
        }

    @Test
    fun `the note is taken once, a skipped reasons step travels as the default, and a resume with no note opens nothing`() =
        runTest {
            handoff.leave(OnboardingHandoffNote(userId = "user-1", reason = null))
            val vm = viewModel()
            val effects = recordEffects(vm.effects)

            vm.onResumed()
            advanceUntilIdle()
            assertEquals(listOf<HomeMomentEffect>(HomeMomentEffect.OpenMomentPage(ONBOARDING_PROMPT_DEFAULT_SLUG)), effects.values)

            vm.onResumed()
            advanceUntilIdle()
            effects.stop()
            assertEquals(1, effects.values.size)
            assertEquals(1, reference.refreshSoulsCount)
        }

    @Test
    fun `a note left for another user is not taken, and is gone afterwards`() =
        runTest {
            handoff.leave(OnboardingHandoffNote(userId = "user-1", reason = OnboardingReason.GOOD_DAYS))

            val other = viewModel(userId = "user-2")
            val otherEffects = recordEffects(other.effects)
            other.onResumed()
            advanceUntilIdle()
            assertEquals(emptyList<HomeMomentEffect>(), otherEffects.values)
            otherEffects.stop()

            val owner = viewModel(userId = "user-1")
            val ownerEffects = recordEffects(owner.effects)
            owner.onResumed()
            advanceUntilIdle()
            assertEquals(emptyList<HomeMomentEffect>(), ownerEffects.values)
            ownerEffects.stop()
        }
}

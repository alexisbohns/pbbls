package app.pbbls.android.features.consent

import app.pbbls.android.R
import app.pbbls.android.core.data.ConsentPreferences
import app.pbbls.android.core.data.DeletionState
import app.pbbls.android.core.data.ReauthMethod
import app.pbbls.android.core.model.ConsentKind
import app.pbbls.android.testing.FakeConsentService
import app.pbbls.android.testing.FakeProfileService
import app.pbbls.android.testing.FakeSupabaseService
import app.pbbls.android.testing.InMemoryPrefs
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.freshSession
import app.pbbls.android.testing.testSession
import io.github.jan.supabase.auth.user.Identity
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class ConsentGateViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val cache = ConsentPreferences(InMemoryPrefs())

    private fun viewModel(
        consents: FakeConsentService = FakeConsentService(),
        profile: FakeProfileService = FakeProfileService(),
        supabase: FakeSupabaseService = FakeSupabaseService(session = freshSession(), isInitializing = false),
    ) = ConsentGateViewModel(consents, cache, profile, supabase)

    @Test
    fun `no user is Idle and never checks`() =
        runTest {
            val consents = FakeConsentService()
            val vm = viewModel(consents)
            vm.start(null)
            advanceUntilIdle()
            assertEquals(ConsentGateUiState.Idle, vm.uiState.value)
            assertEquals(0, consents.activeCalls)
        }

    @Test
    fun `a satisfied ledger passes and is cached`() =
        runTest {
            val vm = viewModel()
            vm.start("user-1")
            advanceUntilIdle()
            assertEquals(ConsentGateUiState.Satisfied("user-1"), vm.uiState.value)
            assertTrue(cache.isSatisfied("user-1", ConsentGateLogic.fingerprint()))
        }

    @Test
    fun `a cached pass skips the network`() =
        runTest {
            cache.markSatisfied("user-1", ConsentGateLogic.fingerprint())
            val consents = FakeConsentService(rows = mutableListOf())
            val vm = viewModel(consents)
            vm.start("user-1")
            advanceUntilIdle()
            assertEquals(ConsentGateUiState.Satisfied("user-1"), vm.uiState.value)
            assertEquals(0, consents.activeCalls)
        }

    @Test
    fun `an empty ledger asks for all four`() =
        runTest {
            val vm = viewModel(FakeConsentService(rows = mutableListOf()))
            vm.start("user-1")
            advanceUntilIdle()
            val state = vm.uiState.value as ConsentGateUiState.Required
            assertEquals(ConsentGateLogic.REQUIRED, state.missing)
            assertEquals(false, state.canContinue)
        }

    @Test
    fun `continue records each missing act and passes`() =
        runTest {
            val consents = FakeConsentService(rows = mutableListOf())
            val vm = viewModel(consents)
            vm.start("user-1")
            advanceUntilIdle()
            ConsentGateLogic.REQUIRED.forEach { vm.onToggle(it, true) }

            vm.onContinue()
            advanceUntilIdle()

            assertEquals(ConsentGateUiState.Satisfied("user-1"), vm.uiState.value)
            assertEquals(ConsentGateLogic.REQUIRED, consents.recordCalls.map { it.first })
            assertTrue(consents.recordCalls.all { it.third == "android_settings" })
            assertEquals("1.2.0", consents.recordCalls.first { it.first == ConsentKind.TERMS }.second)
        }

    @Test
    fun `continue does nothing until every shown box is ticked`() =
        runTest {
            val consents = FakeConsentService(rows = mutableListOf())
            val vm = viewModel(consents)
            vm.start("user-1")
            advanceUntilIdle()
            vm.onToggle(ConsentKind.TERMS, true)
            vm.onContinue()
            advanceUntilIdle()
            assertTrue(consents.recordCalls.isEmpty())
        }

    @Test
    fun `only the outdated act is asked for`() =
        runTest {
            val rows = FakeConsentService.satisfied()
            rows.replaceAll { if (it.kind == "terms") it.copy(documentVersion = "1.0.0") else it }
            val vm = viewModel(FakeConsentService(rows = rows))
            vm.start("user-1")
            advanceUntilIdle()
            assertEquals(listOf(ConsentKind.TERMS), (vm.uiState.value as ConsentGateUiState.Required).missing)
        }

    @Test
    fun `a failed check fails closed, and retry recovers`() =
        runTest {
            val consents = FakeConsentService()
            consents.activeFailure = IOException("offline")
            val vm = viewModel(consents)
            vm.start("user-1")
            advanceUntilIdle()
            assertEquals(ConsentGateUiState.Failed(R.string.error_offline), vm.uiState.value)

            consents.activeFailure = null
            vm.retry()
            advanceUntilIdle()
            assertEquals(ConsentGateUiState.Satisfied("user-1"), vm.uiState.value)
        }

    @Test
    fun `a failed record keeps the form and shows an error`() =
        runTest {
            val consents = FakeConsentService(rows = mutableListOf())
            consents.recordFailure = IOException("offline")
            val vm = viewModel(consents)
            vm.start("user-1")
            advanceUntilIdle()
            ConsentGateLogic.REQUIRED.forEach { vm.onToggle(it, true) }
            vm.onContinue()
            advanceUntilIdle()
            val state = vm.uiState.value as ConsentGateUiState.Required
            assertEquals(R.string.error_offline, state.errorRes)
            assertEquals(false, state.isSubmitting)
            assertEquals(ConsentGateLogic.REQUIRED.toSet(), state.ticked)
        }

    @Test
    fun `a new user re-checks`() =
        runTest {
            val consents = FakeConsentService()
            val vm = viewModel(consents)
            vm.start("user-1")
            advanceUntilIdle()
            vm.start("user-2")
            advanceUntilIdle()
            assertEquals(ConsentGateUiState.Satisfied("user-2"), vm.uiState.value)
            assertEquals(2, consents.activeCalls)
        }

    // MARK: - Deleting from the gate (#1030)

    @Test
    fun `a declining user can delete their account without recording consent`() =
        runTest {
            val consents = FakeConsentService(rows = mutableListOf())
            val profile = FakeProfileService()
            val supabase = FakeSupabaseService(session = freshSession(), isInitializing = false)
            val vm = viewModel(consents, profile, supabase)
            vm.start("user-1")
            advanceUntilIdle()

            vm.requestDelete()
            assertEquals(DeletionState.CONFIRMING, vm.deletion.value.deletion)
            vm.confirmDelete()
            advanceUntilIdle()

            assertEquals(1, profile.deleteAccountCount)
            assertEquals(1, supabase.signOutCount)
            assertTrue(consents.recordCalls.isEmpty())
            assertTrue(vm.uiState.value is ConsentGateUiState.Required)
        }

    @Test
    fun `a stale sign-in confirms it is you before deleting`() =
        runTest {
            val profile = FakeProfileService()
            val emailIdentity = Identity(id = "i", identityData = JsonObject(emptyMap()), provider = "email", userId = "user-1")
            val stale = testSession().copy(user = testSession().user?.copy(identities = listOf(emailIdentity)))
            val supabase = FakeSupabaseService(session = stale, isInitializing = false)
            val vm = viewModel(FakeConsentService(rows = mutableListOf()), profile, supabase)
            vm.start("user-1")
            advanceUntilIdle()

            vm.requestDelete()
            vm.confirmDelete()
            advanceUntilIdle()
            assertEquals(DeletionState.REAUTHENTICATING, vm.deletion.value.deletion)
            assertEquals(
                ReauthMethod.PASSWORD,
                vm.deletion.value.reauth
                    ?.method,
            )
            assertEquals(0, profile.deleteAccountCount)

            vm.onReauthPasswordChange("hunter2")
            vm.submitReauth()
            advanceUntilIdle()

            assertEquals(listOf("hunter2"), supabase.reauthCalls)
            assertEquals(1, profile.deleteAccountCount)
            assertEquals(1, supabase.signOutCount)
        }

    @Test
    fun `cancelling the deletion keeps the gate and deletes nothing`() =
        runTest {
            val profile = FakeProfileService()
            val vm = viewModel(FakeConsentService(rows = mutableListOf()), profile)
            vm.start("user-1")
            advanceUntilIdle()

            vm.requestDelete()
            vm.cancelDelete()
            advanceUntilIdle()

            assertEquals(DeletionState.IDLE, vm.deletion.value.deletion)
            assertEquals(0, profile.deleteAccountCount)
            assertTrue(vm.uiState.value is ConsentGateUiState.Required)
        }

    @Test
    fun `a failed deletion says so and keeps the gate`() =
        runTest {
            val profile = FakeProfileService().apply { failNext = IOException("offline") }
            val supabase = FakeSupabaseService(session = freshSession(), isInitializing = false)
            val vm = viewModel(FakeConsentService(rows = mutableListOf()), profile, supabase)
            vm.start("user-1")
            advanceUntilIdle()

            vm.requestDelete()
            vm.confirmDelete()
            advanceUntilIdle()

            assertEquals(DeletionState.FAILED, vm.deletion.value.deletion)
            assertEquals(0, supabase.signOutCount)
            assertTrue(vm.uiState.value is ConsentGateUiState.Required)
        }

    @Test
    fun `a deletion dialog left open never carries over to the next user`() =
        runTest {
            val vm =
                viewModel(
                    FakeConsentService(rows = mutableListOf()),
                    supabase = FakeSupabaseService(session = testSession(), isInitializing = false),
                )
            vm.start("user-1")
            advanceUntilIdle()
            vm.requestDelete()
            vm.confirmDelete()
            assertEquals(DeletionState.REAUTHENTICATING, vm.deletion.value.deletion)

            vm.start(null)
            vm.start("user-2")
            advanceUntilIdle()

            assertEquals(DeletionState.IDLE, vm.deletion.value.deletion)
            assertNull(vm.deletion.value.reauth)
        }
}

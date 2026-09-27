package app.pbbls.android.features.consent

import app.pbbls.android.R
import app.pbbls.android.core.data.ConsentPreferences
import app.pbbls.android.core.model.ConsentKind
import app.pbbls.android.testing.FakeConsentService
import app.pbbls.android.testing.FakeSupabaseService
import app.pbbls.android.testing.InMemoryPrefs
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.testSession
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
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
        session: UserSession? = testSession(),
    ) = ConsentGateViewModel(consents, cache, FakeSupabaseService(session = session, isInitializing = false))

    private fun googleSession() =
        testSession().copy(
            user = UserInfo(id = "user-1", aud = "authenticated", appMetadata = buildJsonObject { put("provider", "google") }),
        )

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
            assertEquals("1.1.0", consents.recordCalls.first { it.first == ConsentKind.TERMS }.second)
        }

    @Test
    fun `a google account records as android_oauth`() =
        runTest {
            val consents = FakeConsentService(rows = mutableListOf())
            val vm = viewModel(consents, session = googleSession())
            vm.start("user-1")
            advanceUntilIdle()
            ConsentGateLogic.REQUIRED.forEach { vm.onToggle(it, true) }
            vm.onContinue()
            advanceUntilIdle()
            assertTrue(consents.recordCalls.all { it.third == "android_oauth" })
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
}

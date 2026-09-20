package app.pbbls.android.features.auth

import androidx.lifecycle.SavedStateHandle
import app.pbbls.android.R
import app.pbbls.android.testing.FakeSupabaseService
import app.pbbls.android.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** The sign-in / sign-up form's state, guard and submit contract (#849). */
class AuthViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        supabase: FakeSupabaseService = FakeSupabaseService(),
        savedState: SavedStateHandle = SavedStateHandle(),
    ) = AuthViewModel(savedState, supabase)

    /** Fills a sign-up form to the point where it may be submitted. */
    private fun AuthViewModel.fillSignUp() {
        onModeChange(AuthMode.SIGNUP)
        onEmailChange("someone@example.com")
        onPasswordChange("hunter2hunter2")
        onTermsChange(true)
        onPrivacyChange(true)
    }

    // MARK: - Seeding

    @Test
    fun `start seeds the route's mode`() =
        runTest {
            val viewModel = viewModel()
            viewModel.start(AuthMode.SIGNUP)

            assertEquals(AuthMode.SIGNUP, viewModel.uiState.value.mode)
        }

    /**
     * The mode is a choice once the screen is open, not a route parameter: a
     * rotation must not put the user back on Login after they picked Sign up.
     */
    @Test
    fun `start does not undo a mode the user chose`() =
        runTest {
            val viewModel = viewModel()
            viewModel.start(AuthMode.LOGIN)
            viewModel.onModeChange(AuthMode.SIGNUP)

            viewModel.start(AuthMode.LOGIN)

            assertEquals(AuthMode.SIGNUP, viewModel.uiState.value.mode)
        }

    // MARK: - The form

    /** Product policy: `+` is stripped and explained, lowercasing is silent. */
    @Test
    fun `a plus is stripped and explained`() =
        runTest {
            val viewModel = viewModel()
            viewModel.start(AuthMode.LOGIN)

            viewModel.onEmailChange("Someone+tag@Example.com")

            val state = viewModel.uiState.value
            assertEquals("someonetag@example.com", state.email)
            assertTrue(state.showPlusError)
        }

    @Test
    fun `an email without a plus explains nothing`() =
        runTest {
            val viewModel = viewModel()
            viewModel.start(AuthMode.LOGIN)

            viewModel.onEmailChange("Someone@Example.com")

            val state = viewModel.uiState.value
            assertEquals("someone@example.com", state.email)
            assertFalse(state.showPlusError)
        }

    /** The consents belong to a sign-up, so switching to Login drops them. */
    @Test
    fun `switching to Login clears the consents`() =
        runTest {
            val viewModel = viewModel()
            viewModel.start(AuthMode.SIGNUP)
            viewModel.onTermsChange(true)
            viewModel.onPrivacyChange(true)

            viewModel.onModeChange(AuthMode.LOGIN)

            val state = viewModel.uiState.value
            assertFalse(state.termsAccepted)
            assertFalse(state.privacyAccepted)
        }

    @Test
    fun `a sign-up cannot be submitted without both consents`() =
        runTest {
            val supabase = FakeSupabaseService()
            val viewModel = viewModel(supabase)
            viewModel.start(AuthMode.SIGNUP)
            viewModel.onEmailChange("someone@example.com")
            viewModel.onPasswordChange("hunter2hunter2")
            viewModel.onTermsChange(true)

            assertFalse(viewModel.uiState.value.canSubmit)
            viewModel.submit()
            advanceUntilIdle()
            assertTrue(supabase.signUpCalls.isEmpty())
        }

    // MARK: - Submit

    @Test
    fun `submitting a login calls signIn with the trimmed email`() =
        runTest {
            val supabase = FakeSupabaseService()
            val viewModel = viewModel(supabase)
            viewModel.start(AuthMode.LOGIN)
            viewModel.onEmailChange("someone@example.com")
            viewModel.onPasswordChange("hunter2hunter2")

            viewModel.submit()
            advanceUntilIdle()

            assertEquals(listOf("someone@example.com" to "hunter2hunter2"), supabase.signInCalls)
            assertTrue(supabase.signUpCalls.isEmpty())
        }

    @Test
    fun `submitting a sign-up calls signUp`() =
        runTest {
            val supabase = FakeSupabaseService()
            val viewModel = viewModel(supabase)
            viewModel.start(AuthMode.LOGIN)
            viewModel.fillSignUp()

            viewModel.submit()
            advanceUntilIdle()

            assertEquals(1, supabase.signUpCalls.size)
            assertTrue(supabase.signInCalls.isEmpty())
        }

    /**
     * **What rotation used to cost.** `isSubmitting` was a plain `remember`, so
     * turning the phone mid-request reset it and re-enabled the button — a
     * second sign-up could be fired while the first was still in flight.
     */
    @Test
    fun `a second submit is refused while one is in flight`() =
        runTest {
            val supabase = FakeSupabaseService()
            val viewModel = viewModel(supabase)
            viewModel.start(AuthMode.LOGIN)
            viewModel.fillSignUp()

            viewModel.submit()
            assertTrue(viewModel.uiState.value.isSubmitting)
            assertFalse("the button is the guard", viewModel.uiState.value.canSubmit)

            viewModel.submit()
            advanceUntilIdle()

            assertEquals(1, supabase.signUpCalls.size)
        }

    @Test
    fun `a failed submit frees the button and shows a resource id`() =
        runTest {
            val supabase = FakeSupabaseService()
            supabase.failNext = IOException("offline")
            val viewModel = viewModel(supabase)
            viewModel.start(AuthMode.LOGIN)
            viewModel.onEmailChange("someone@example.com")
            viewModel.onPasswordChange("hunter2hunter2")

            viewModel.submit()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isSubmitting)
            assertEquals(R.string.error_offline, state.authErrorRes)
        }

    /** Typing again clears the verdict, so it never sits over an edited field. */
    @Test
    fun `editing the email clears the error`() =
        runTest {
            val supabase = FakeSupabaseService()
            supabase.failNext = IOException("offline")
            val viewModel = viewModel(supabase)
            viewModel.start(AuthMode.LOGIN)
            viewModel.onEmailChange("someone@example.com")
            viewModel.onPasswordChange("hunter2hunter2")
            viewModel.submit()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.authErrorRes != null)

            viewModel.onEmailChange("other@example.com")

            assertNull(viewModel.uiState.value.authErrorRes)
        }

    @Test
    fun `google sign-in is refused while a submit is in flight`() =
        runTest {
            val supabase = FakeSupabaseService()
            val viewModel = viewModel(supabase)
            viewModel.start(AuthMode.LOGIN)
            viewModel.onEmailChange("someone@example.com")
            viewModel.onPasswordChange("hunter2hunter2")

            viewModel.submit()
            viewModel.signInWithGoogle()
            advanceUntilIdle()

            assertEquals(0, supabase.googleSignInCount)
        }

    // MARK: - SavedStateHandle

    /**
     * The typed email and the consents survive process death; the password
     * deliberately does not — the saved-instance-state `Bundle` is persisted to
     * disk, and a password sitting in it would outlive the screen on storage.
     */
    @Test
    fun `the email and consents survive process death, the password does not`() =
        runTest {
            val savedState = SavedStateHandle()
            val first = viewModel(savedState = savedState)
            // The route opened on Login and the user switched — that switch is
            // the thing worth restoring. A route-supplied mode needs no
            // persisting: the back stack hands it back with its arguments.
            first.start(AuthMode.LOGIN)
            first.onModeChange(AuthMode.SIGNUP)
            first.onEmailChange("someone@example.com")
            first.onPasswordChange("hunter2hunter2")
            first.onTermsChange(true)

            val restored = viewModel(savedState = savedState)
            restored.start(AuthMode.LOGIN)

            val state = restored.uiState.value
            assertEquals("someone@example.com", state.email)
            assertTrue(state.termsAccepted)
            assertEquals("", state.password)
            assertEquals(AuthMode.SIGNUP, state.mode)
        }
}

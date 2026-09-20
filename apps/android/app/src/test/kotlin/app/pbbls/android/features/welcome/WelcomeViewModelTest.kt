package app.pbbls.android.features.welcome

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

/** Welcome's one button: the Google flow's guard and its error (#849). */
class WelcomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(supabase: FakeSupabaseService = FakeSupabaseService()) = WelcomeViewModel(supabase)

    @Test
    fun `signing in calls supabase once`() =
        runTest {
            val supabase = FakeSupabaseService()
            val viewModel = viewModel(supabase)

            viewModel.signInWithGoogle()
            advanceUntilIdle()

            assertEquals(1, supabase.googleSignInCount)
            assertFalse(viewModel.uiState.value.isSubmitting)
        }

    /**
     * **What rotation used to cost.** `isSubmitting` was a plain `remember`, so
     * turning the phone while the OAuth sheet was open re-enabled the button.
     */
    @Test
    fun `a second tap is refused while one is in flight`() =
        runTest {
            val supabase = FakeSupabaseService()
            val viewModel = viewModel(supabase)

            viewModel.signInWithGoogle()
            assertTrue(viewModel.uiState.value.isSubmitting)
            viewModel.signInWithGoogle()
            advanceUntilIdle()

            assertEquals(1, supabase.googleSignInCount)
        }

    @Test
    fun `a failure frees the button and shows a resource id`() =
        runTest {
            val supabase = FakeSupabaseService()
            supabase.failNext = IOException("offline")
            val viewModel = viewModel(supabase)

            viewModel.signInWithGoogle()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isSubmitting)
            assertEquals(R.string.error_offline, state.authErrorRes)

            viewModel.dismissError()
            assertNull(viewModel.uiState.value.authErrorRes)
        }

    /** A later attempt clears the previous verdict rather than stacking on it. */
    @Test
    fun `a retry clears the previous error`() =
        runTest {
            val supabase = FakeSupabaseService()
            supabase.failNext = IOException("offline")
            val viewModel = viewModel(supabase)
            viewModel.signInWithGoogle()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.authErrorRes != null)

            viewModel.signInWithGoogle()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.authErrorRes)
            assertEquals(2, supabase.googleSignInCount)
        }
}

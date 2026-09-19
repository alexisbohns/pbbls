package app.pbbls.android.features.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.pbbls.android.R
import app.pbbls.android.testing.FakeProfileService
import app.pbbls.android.testing.FakeSupabaseService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.postgrestException
import app.pbbls.android.testing.recordEffects
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * Settings' save sequence, deletion flow and form persistence (#849).
 *
 * None of it was reachable before: it lived in a 672-line composable that read
 * two `Local…Service`s and held twelve `remember`s.
 */
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun initial(
        displayName: String = "Pebbler",
        handle: String? = null,
        publicProfile: Boolean = false,
    ) = SettingsInitial(displayName = displayName, handle = handle, publicProfile = publicProfile)

    private fun viewModel(
        profile: FakeProfileService = FakeProfileService(),
        supabase: FakeSupabaseService = FakeSupabaseService(),
        savedState: SavedStateHandle = SavedStateHandle(),
    ) = SettingsViewModel(savedState, profile, supabase)

    // MARK: - The save sequence

    @Test
    fun `claiming a handle and going public writes the handle first`() =
        runTest {
            val profile = FakeProfileService()
            val viewModel = viewModel(profile)
            viewModel.start(initial())

            viewModel.onHandleChange("pebbler")
            viewModel.onPublicProfileChange(true)
            viewModel.save()
            advanceUntilIdle()

            // The order is load-bearing: public_profile has a DB CHECK that the
            // handle must already exist.
            assertEquals(listOf("pebbler"), profile.setHandleCalls)
            assertEquals(listOf(true), profile.setPublicProfileCalls)
            assertEquals(1, profile.saveSettingsCalls.size)
        }

    /**
     * **The regression this ViewModel exists for.**
     *
     * `save()` ran in `rememberCoroutineScope`, so leaving the screen between
     * `set_handle` and `set_public_profile` cancelled the coroutine and left the
     * handle claimed with the toggle unwritten — a half-applied save the user
     * cannot repeat, because the handle they wanted is now taken by themselves.
     *
     * Clearing the `ViewModelStore` is the closest a unit test gets to that:
     * it calls `onCleared`, which cancels `viewModelScope`. With the sequence
     * inside `withContext(NonCancellable)` the remaining calls still land.
     */
    @Test
    fun `clearing the ViewModel mid-save still finishes the sequence`() =
        runTest {
            val profile = FakeProfileService()
            val gate = CompletableDeferred<Unit>()
            profile.setHandleGate = gate

            val store = ViewModelStore()
            val viewModel = viewModel(profile)
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
                },
            )[SettingsViewModel::class.java]

            viewModel.start(initial())
            viewModel.onHandleChange("pebbler")
            viewModel.onPublicProfileChange(true)
            viewModel.save()
            advanceUntilIdle()

            // In flight at the first server call: the handle request has left.
            assertEquals(listOf("pebbler"), profile.setHandleCalls)
            assertTrue(profile.setPublicProfileCalls.isEmpty())

            // The user leaves. Before #849 this ended the save right here.
            store.clear()
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                "the toggle must still be written, or the handle is claimed for nothing",
                listOf(true),
                profile.setPublicProfileCalls,
            )
            assertEquals(1, profile.saveSettingsCalls.size)
        }

    @Test
    fun `a rejected handle stops the sequence before anything else is written`() =
        runTest {
            val profile = FakeProfileService()
            profile.failNext = postgrestException("handle_taken")
            val viewModel = viewModel(profile)
            viewModel.start(initial())

            viewModel.onHandleChange("taken")
            viewModel.onPublicProfileChange(true)
            viewModel.save()
            advanceUntilIdle()

            assertTrue("no profile write may follow a failed claim", profile.saveSettingsCalls.isEmpty())
            assertTrue(profile.setPublicProfileCalls.isEmpty())
            val state = viewModel.uiState.value
            assertEquals(R.string.settings_handle_error_taken, state.handleErrorRes)
            assertFalse(state.isSaving)
            // A field-level verdict is not the generic save banner.
            assertFalse(state.didSaveFail)
        }

    @Test
    fun `a transport failure on the handle shows the generic error, not a handle verdict`() =
        runTest {
            val profile = FakeProfileService()
            profile.failNext = IOException("offline")
            val viewModel = viewModel(profile)
            viewModel.start(initial())

            viewModel.onHandleChange("pebbler")
            viewModel.save()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull(state.handleErrorRes)
            assertTrue(state.didSaveFail)
        }

    @Test
    fun `releasing the handle never writes the public toggle`() =
        runTest {
            val profile = FakeProfileService()
            val viewModel = viewModel(profile)
            viewModel.start(initial(handle = "pebbler", publicProfile = true))

            viewModel.onHandleChange("")
            viewModel.save()
            advanceUntilIdle()

            assertEquals(listOf<String?>(null), profile.setHandleCalls)
            // Releasing already clears the flag server-side.
            assertTrue(profile.setPublicProfileCalls.isEmpty())
        }

    @Test
    fun `a successful save reports the new values`() =
        runTest {
            val viewModel = viewModel()
            val effects = recordEffects(viewModel.effects)
            viewModel.start(initial())

            viewModel.onDisplayNameChange("Sam")
            viewModel.save()
            advanceUntilIdle()

            val saved = effects.values.filterIsInstance<SettingsEffect.Saved>().single()
            assertEquals("Sam", saved.displayName)
            effects.stop()
        }

    @Test
    fun `saving a pristine form does nothing`() =
        runTest {
            val profile = FakeProfileService()
            val viewModel = viewModel(profile)
            viewModel.start(initial())

            viewModel.save()
            advanceUntilIdle()

            assertTrue(profile.saveSettingsCalls.isEmpty())
        }

    // MARK: - The form invariant

    /**
     * `public_profile` has a DB CHECK requiring a handle, so the form must never
     * be able to describe "public, no handle". This used to live in the text
     * field's `onValueChange`, where it was one layout edit from being lost.
     */
    @Test
    fun `emptying the handle drops the public flag with it`() =
        runTest {
            val viewModel = viewModel()
            viewModel.start(initial(handle = "pebbler", publicProfile = true))
            assertTrue(viewModel.uiState.value.form.isPublicProfile)

            viewModel.onHandleChange("   ")

            assertFalse(viewModel.uiState.value.form.isPublicProfile)
        }

    @Test
    fun `editing the handle clears the previous verdict`() =
        runTest {
            val profile = FakeProfileService()
            profile.failNext = postgrestException("handle_taken")
            val viewModel = viewModel(profile)
            viewModel.start(initial())
            viewModel.onHandleChange("taken")
            viewModel.save()
            advanceUntilIdle()
            assertEquals(R.string.settings_handle_error_taken, viewModel.uiState.value.handleErrorRes)

            viewModel.onHandleChange("taken2")

            assertNull(viewModel.uiState.value.handleErrorRes)
        }

    // MARK: - SavedStateHandle

    @Test
    fun `typed fields survive process death`() =
        runTest {
            val savedState = SavedStateHandle()
            viewModel(savedState = savedState).apply {
                start(initial())
                onDisplayNameChange("Sam")
                onHandleChange("sam")
                onPublicProfileChange(true)
            }

            // The process dies; a new ViewModel is built with the same handle.
            val restored = viewModel(savedState = savedState)
            restored.start(initial())

            val form = restored.uiState.value.form
            assertEquals("Sam", form.displayName)
            assertEquals("sam", form.handle)
            assertTrue(form.isPublicProfile)
        }

    /**
     * `SavedStateHandle` is written into the saved-instance-state `Bundle`,
     * which Android persists to disk. A new password must not be in it.
     */
    @Test
    fun `the new password is never written to SavedStateHandle`() =
        runTest {
            val savedState = SavedStateHandle()
            val viewModel = viewModel(savedState = savedState)
            viewModel.start(initial())

            viewModel.onPasswordChange("hunter2")

            assertTrue(viewModel.uiState.value.form.newPassword == "hunter2")
            assertTrue(
                "no key may hold the password",
                savedState.keys().none { savedState.get<Any?>(it) == "hunter2" },
            )
        }

    @Test
    fun `start does not re-seed over what the user has typed`() =
        runTest {
            val viewModel = viewModel()
            viewModel.start(initial(displayName = "Pebbler"))
            viewModel.onDisplayNameChange("Sam")

            // A rotation re-runs the screen's LaunchedEffect.
            viewModel.start(initial(displayName = "Pebbler"))

            assertEquals("Sam", viewModel.uiState.value.form.displayName)
        }

    // MARK: - Deletion

    @Test
    fun `deletion walks confirm to purge to sign-out`() =
        runTest {
            val profile = FakeProfileService()
            val supabase = FakeSupabaseService()
            val viewModel = viewModel(profile, supabase)
            viewModel.start(initial())

            viewModel.requestDelete()
            assertEquals(DeletionState.CONFIRMING, viewModel.uiState.value.deletion)

            viewModel.confirmDelete()
            advanceUntilIdle()

            assertEquals(1, profile.deleteAccountCount)
            assertEquals(1, supabase.signOutCount)
        }

    @Test
    fun `a failed deletion surfaces the error and leaves the session alone`() =
        runTest {
            val profile = FakeProfileService()
            profile.failNext = IOException("offline")
            val supabase = FakeSupabaseService()
            val viewModel = viewModel(profile, supabase)
            viewModel.start(initial())

            viewModel.requestDelete()
            viewModel.confirmDelete()
            advanceUntilIdle()

            assertEquals(DeletionState.FAILED, viewModel.uiState.value.deletion)
            assertEquals(0, supabase.signOutCount)

            viewModel.dismissDeleteError()
            assertEquals(DeletionState.IDLE, viewModel.uiState.value.deletion)
        }

    @Test
    fun `cancelling the confirmation deletes nothing`() =
        runTest {
            val profile = FakeProfileService()
            val viewModel = viewModel(profile)
            viewModel.start(initial())

            viewModel.requestDelete()
            viewModel.cancelDelete()
            advanceUntilIdle()

            assertEquals(DeletionState.IDLE, viewModel.uiState.value.deletion)
            assertEquals(0, profile.deleteAccountCount)
        }
}

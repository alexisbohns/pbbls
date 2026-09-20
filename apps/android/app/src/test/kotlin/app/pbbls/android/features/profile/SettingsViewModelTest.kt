package app.pbbls.android.features.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.pbbls.android.R
import app.pbbls.android.services.ProfileRow
import app.pbbls.android.testing.FakeProfileService
import app.pbbls.android.testing.FakeSupabaseService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.postgrestException
import app.pbbls.android.testing.recordEffects
import io.github.jan.supabase.auth.user.Identity
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.OffsetDateTime

private const val USER_ID = "user-1"

/**
 * Settings' own load, its save sequence, deletion flow and form persistence
 * (#849, #852).
 *
 * None of it was reachable before: it lived in a 672-line composable that read
 * two `Local…Service`s and held twelve `remember`s. As of Task 16 the load
 * itself moved in too — `SettingsScreen` has no id to hand it, unlike the soul
 * and collection forms (Task 15), so this ViewModel fetches the profile and
 * the session identity by itself rather than being seeded by `ProfileScreen`.
 */
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun profileRow(
        displayName: String = "Pebbler",
        glyphId: String? = null,
        handle: String? = null,
        publicProfile: Boolean = false,
    ) = ProfileRow(
        displayName = displayName,
        createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        glyphId = glyphId,
        handle = handle,
        publicProfile = publicProfile,
    )

    private fun session(
        email: String? = "pebbler@example.com",
        providers: List<String> = listOf("google"),
    ) = UserSession(
        accessToken = "token",
        refreshToken = "refresh",
        expiresIn = 3600,
        tokenType = "bearer",
        user =
            UserInfo(
                id = USER_ID,
                aud = "authenticated",
                email = email,
                identities =
                    providers.map { provider ->
                        Identity(
                            id = "identity-$provider",
                            identityData = JsonObject(emptyMap()),
                            provider = provider,
                            userId = USER_ID,
                        )
                    },
            ),
    )

    private fun viewModel(
        profile: FakeProfileService = FakeProfileService(profile = profileRow()),
        supabase: FakeSupabaseService = FakeSupabaseService(),
        savedState: SavedStateHandle = SavedStateHandle(),
    ) = SettingsViewModel(savedState, profile, supabase)

    // MARK: - Loading its own profile (#852)

    @Test
    fun `settings loads the profile itself`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow(displayName = "Sam", handle = "sam"))
            val viewModel = viewModel(profile)

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertNull(state.loadErrorRes)
            assertEquals("Sam", state.form.displayName)
            assertEquals("sam", state.form.handle)
            assertEquals(1, profile.loadProfileCount)
        }

    @Test
    fun `settings loads email and providers from the session source`() =
        runTest {
            val supabase = FakeSupabaseService(session = session(email = "sam@pbbls.app", providers = listOf("apple")))
            val viewModel = viewModel(supabase = supabase)

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("sam@pbbls.app", state.initial.email)
            assertEquals(listOf("Apple"), state.initial.providers)
        }

    @Test
    fun `a failed profile load surfaces the error state, not a blank form`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            profile.failNext = IOException("offline")
            val viewModel = viewModel(profile)

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertEquals(R.string.settings_load_error, state.loadErrorRes)
        }

    // MARK: - The save sequence

    @Test
    fun `claiming a handle and going public writes the handle first`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val viewModel = viewModel(profile)
            advanceUntilIdle()

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
            val profile = FakeProfileService(profile = profileRow())
            val viewModel = viewModel(profile)
            advanceUntilIdle()

            val gate = CompletableDeferred<Unit>()
            profile.setHandleGate = gate

            val store = ViewModelStore()
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
                },
            )[SettingsViewModel::class.java]

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
            val profile = FakeProfileService(profile = profileRow())
            val viewModel = viewModel(profile)
            advanceUntilIdle()
            profile.failNext = postgrestException("handle_taken")

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
            val profile = FakeProfileService(profile = profileRow())
            val viewModel = viewModel(profile)
            advanceUntilIdle()
            profile.failNext = IOException("offline")

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
            val profile = FakeProfileService(profile = profileRow(handle = "pebbler", publicProfile = true))
            val viewModel = viewModel(profile)
            advanceUntilIdle()

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
            advanceUntilIdle()
            val effects = recordEffects(viewModel.effects)

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
            val profile = FakeProfileService(profile = profileRow())
            val viewModel = viewModel(profile)
            advanceUntilIdle()

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
            val profile = FakeProfileService(profile = profileRow(handle = "pebbler", publicProfile = true))
            val viewModel = viewModel(profile)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.form.isPublicProfile)

            viewModel.onHandleChange("   ")

            assertFalse(viewModel.uiState.value.form.isPublicProfile)
        }

    @Test
    fun `editing the handle clears the previous verdict`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val viewModel = viewModel(profile)
            advanceUntilIdle()
            profile.failNext = postgrestException("handle_taken")
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
            val viewModel = viewModel(savedState = savedState)
            advanceUntilIdle()
            viewModel.onDisplayNameChange("Sam")
            viewModel.onHandleChange("sam")
            viewModel.onPublicProfileChange(true)

            // The process dies; a new ViewModel is built with the same handle.
            val restored = viewModel(savedState = savedState)
            advanceUntilIdle()

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
            advanceUntilIdle()

            viewModel.onPasswordChange("hunter2")

            assertTrue(viewModel.uiState.value.form.newPassword == "hunter2")
            assertTrue(
                "no key may hold the password",
                savedState.keys().none { savedState.get<Any?>(it) == "hunter2" },
            )
        }

    // MARK: - Deletion

    @Test
    fun `deletion walks confirm to purge to sign-out`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val supabase = FakeSupabaseService()
            val viewModel = viewModel(profile, supabase)
            advanceUntilIdle()

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
            val profile = FakeProfileService(profile = profileRow())
            val supabase = FakeSupabaseService()
            val viewModel = viewModel(profile, supabase)
            advanceUntilIdle()
            profile.failNext = IOException("offline")

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
            val profile = FakeProfileService(profile = profileRow())
            val viewModel = viewModel(profile)
            advanceUntilIdle()

            viewModel.requestDelete()
            viewModel.cancelDelete()
            advanceUntilIdle()

            assertEquals(DeletionState.IDLE, viewModel.uiState.value.deletion)
            assertEquals(0, profile.deleteAccountCount)
        }
}

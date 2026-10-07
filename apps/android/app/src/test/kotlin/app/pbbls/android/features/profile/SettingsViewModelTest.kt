package app.pbbls.android.features.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.pbbls.android.R
import app.pbbls.android.core.data.AppearancePreferences
import app.pbbls.android.core.data.DeletionState
import app.pbbls.android.core.data.ProfileRow
import app.pbbls.android.core.data.ReauthAccountMismatchException
import app.pbbls.android.core.data.ReauthMethod
import app.pbbls.android.core.data.ReauthPurpose
import app.pbbls.android.core.data.ReauthRequiredException
import app.pbbls.android.core.model.HealthDataConsent
import app.pbbls.android.testing.FakeConsentService
import app.pbbls.android.testing.FakeProfileService
import app.pbbls.android.testing.FakeSupabaseService
import app.pbbls.android.testing.InMemoryPrefs
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.accessTokenSignedInAt
import app.pbbls.android.testing.authRestException
import app.pbbls.android.testing.postgrestException
import app.pbbls.android.testing.recordEffects
import io.github.jan.supabase.auth.user.Identity
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.ktor.http.HttpStatusCode
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
 * two `Local…Service`s and held twelve `remember`s. Since #852 the load
 * itself moved in too — `SettingsScreen` has no id to hand it, unlike the soul
 * and collection forms (#852), so this ViewModel fetches the profile and
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
        fresh: Boolean = true,
    ) = UserSession(
        accessToken =
            accessTokenSignedInAt(
                if (fresh) {
                    java.time.Instant.now()
                } else {
                    java.time.Instant
                        .now()
                        .minus(java.time.Duration.ofHours(2))
                },
            ),
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
        appearance: AppearancePreferences = AppearancePreferences(InMemoryPrefs()),
        consents: FakeConsentService = FakeConsentService(),
    ) = SettingsViewModel(savedState, profile, supabase, appearance, consents)

    // MARK: - Appearance (#853)

    @Test
    fun `wallpaper switch writes through to appearance preferences`() {
        val appearance = AppearancePreferences(InMemoryPrefs())
        val vm = viewModel(appearance = appearance)
        vm.onUseWallpaperColorsChange(false)
        assertFalse(appearance.useWallpaperColors)
        assertFalse(vm.useWallpaperColors)
    }

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
            val viewModel = viewModel(profile, FakeSupabaseService(session = session()))
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
            val viewModel = viewModel(profile, FakeSupabaseService(session = session()))
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
            val viewModel = viewModel(profile, FakeSupabaseService(session = session()))
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

    // MARK: - Sign out everywhere (#976)

    @Test
    fun `signing out everywhere asks first, then revokes every session`() =
        runTest {
            val supabase = FakeSupabaseService(session = session())
            val viewModel = viewModel(supabase = supabase)
            advanceUntilIdle()

            viewModel.requestSignOutEverywhere()
            assertEquals(SignOutEverywhereState.CONFIRMING, viewModel.uiState.value.signOutEverywhere)
            assertEquals(0, supabase.signOutEverywhereCount)

            viewModel.confirmSignOutEverywhere()
            advanceUntilIdle()

            assertEquals(1, supabase.signOutEverywhereCount)
        }

    @Test
    fun `cancelling sign out everywhere signs nobody out`() =
        runTest {
            val supabase = FakeSupabaseService(session = session())
            val viewModel = viewModel(supabase = supabase)
            advanceUntilIdle()

            viewModel.requestSignOutEverywhere()
            viewModel.cancelSignOutEverywhere()
            advanceUntilIdle()

            assertEquals(SignOutEverywhereState.IDLE, viewModel.uiState.value.signOutEverywhere)
            assertEquals(0, supabase.signOutCount)
        }

    @Test
    fun `a failed global sign-out says so instead of pretending`() =
        runTest {
            val supabase = FakeSupabaseService(session = session())
            val viewModel = viewModel(supabase = supabase)
            advanceUntilIdle()
            supabase.failNext = IOException("offline")

            viewModel.requestSignOutEverywhere()
            viewModel.confirmSignOutEverywhere()
            advanceUntilIdle()

            assertEquals(SignOutEverywhereState.FAILED, viewModel.uiState.value.signOutEverywhere)
            viewModel.dismissSignOutEverywhereError()
            assertEquals(SignOutEverywhereState.IDLE, viewModel.uiState.value.signOutEverywhere)
        }

    // MARK: - Recent sign-in (#976)

    @Test
    fun `deleting with a stale sign-in asks to re-auth and deletes nothing yet`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val supabase = FakeSupabaseService(session = session(providers = listOf("email"), fresh = false))
            val viewModel = viewModel(profile, supabase)
            advanceUntilIdle()

            viewModel.requestDelete()
            viewModel.confirmDelete()
            advanceUntilIdle()

            assertEquals(DeletionState.REAUTHENTICATING, viewModel.uiState.value.deletion)
            assertEquals(
                ReauthMethod.PASSWORD,
                viewModel.uiState.value.reauth
                    ?.method,
            )
            assertEquals(0, profile.deleteAccountCount)
        }

    @Test
    fun `re-entering the password then deletes and signs out`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val supabase = FakeSupabaseService(session = session(providers = listOf("email"), fresh = false))
            val viewModel = viewModel(profile, supabase)
            advanceUntilIdle()

            viewModel.requestDelete()
            viewModel.confirmDelete()
            viewModel.onReauthPasswordChange("hunter2")
            viewModel.submitReauth()
            advanceUntilIdle()

            assertEquals(listOf("hunter2"), supabase.reauthCalls)
            assertNull(viewModel.uiState.value.reauth)
            assertEquals(1, profile.deleteAccountCount)
            assertEquals(1, supabase.signOutCount)
        }

    @Test
    fun `a fresh sign-in skips the re-auth dialog`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val supabase = FakeSupabaseService(session = session())
            val viewModel = viewModel(profile, supabase)
            advanceUntilIdle()

            viewModel.requestDelete()
            viewModel.confirmDelete()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.reauth)
            assertTrue(supabase.reauthCalls.isEmpty())
            assertEquals(1, profile.deleteAccountCount)
        }

    /** The exception a real GoTrue wrong password produces: 400 `invalid_credentials`. */
    @Test
    fun `a wrong password keeps the dialog open with an error and deletes nothing`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val supabase = FakeSupabaseService(session = session(providers = listOf("email"), fresh = false))
            val viewModel = viewModel(profile, supabase)
            advanceUntilIdle()
            viewModel.requestDelete()
            viewModel.confirmDelete()
            supabase.failNext = authRestException("invalid_credentials")

            viewModel.onReauthPasswordChange("wrong")
            viewModel.submitReauth()
            advanceUntilIdle()

            val reauth = viewModel.uiState.value.reauth
            assertEquals(R.string.reauth_wrong_password, reauth?.errorRes)
            assertFalse(reauth!!.isWorking)
            assertEquals("", reauth.password)
            assertEquals(DeletionState.REAUTHENTICATING, viewModel.uiState.value.deletion)
            assertEquals(0, profile.deleteAccountCount)
        }

    /** Any other 4xx is also a `Conflict`; only `invalid_credentials` blames the password. */
    @Test
    fun `a throttled re-auth is not blamed on the password`() =
        runTest {
            val supabase = FakeSupabaseService(session = session(providers = listOf("email"), fresh = false))
            val viewModel = viewModel(supabase = supabase)
            advanceUntilIdle()
            viewModel.requestDelete()
            viewModel.confirmDelete()
            supabase.failNext = authRestException("over_request_rate_limit", HttpStatusCode.TooManyRequests)

            viewModel.onReauthPasswordChange("right-but-throttled")
            viewModel.submitReauth()
            advanceUntilIdle()

            assertEquals(
                R.string.reauth_error,
                viewModel.uiState.value.reauth
                    ?.errorRes,
            )
        }

    @Test
    fun `cancelling the re-auth returns to idle and deletes nothing`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val supabase = FakeSupabaseService(session = session(fresh = false))
            val viewModel = viewModel(profile, supabase)
            advanceUntilIdle()

            viewModel.requestDelete()
            viewModel.confirmDelete()
            viewModel.cancelReauth()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.reauth)
            assertEquals(DeletionState.IDLE, viewModel.uiState.value.deletion)
            assertEquals(0, profile.deleteAccountCount)
        }

    @Test
    fun `the server asking for a re-auth reopens the dialog instead of failing`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val supabase = FakeSupabaseService(session = session())
            val viewModel = viewModel(profile, supabase)
            advanceUntilIdle()
            profile.failNext = ReauthRequiredException()

            viewModel.requestDelete()
            viewModel.confirmDelete()
            advanceUntilIdle()

            assertEquals(DeletionState.REAUTHENTICATING, viewModel.uiState.value.deletion)
            assertEquals(0, supabase.signOutCount)
        }

    @Test
    fun `a google-only account re-auths with google`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val supabase = FakeSupabaseService(session = session(providers = listOf("google"), fresh = false))
            val viewModel = viewModel(profile, supabase)
            advanceUntilIdle()

            viewModel.requestDelete()
            viewModel.confirmDelete()
            assertEquals(
                ReauthMethod.GOOGLE,
                viewModel.uiState.value.reauth
                    ?.method,
            )
            viewModel.submitReauth()
            advanceUntilIdle()

            assertEquals(1, supabase.googleReauthCount)
            assertEquals(1, profile.deleteAccountCount)
        }

    @Test
    fun `a google re-auth that comes back as someone else stops everything`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val supabase = FakeSupabaseService(session = session(providers = listOf("google"), fresh = false))
            val viewModel = viewModel(profile, supabase)
            advanceUntilIdle()
            viewModel.requestDelete()
            viewModel.confirmDelete()
            supabase.failNext = ReauthAccountMismatchException()

            viewModel.submitReauth()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.reauth)
            assertEquals(DeletionState.IDLE, viewModel.uiState.value.deletion)
            assertEquals(0, profile.deleteAccountCount)
        }

    @Test
    fun `editing only the name never asks for a re-auth`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val supabase = FakeSupabaseService(session = session(fresh = false))
            val viewModel = viewModel(profile, supabase)
            advanceUntilIdle()

            viewModel.onDisplayNameChange("Sam")
            viewModel.save()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.reauth)
            assertEquals(1, profile.saveSettingsCalls.size)
        }

    @Test
    fun `a new password with a stale sign-in re-auths first, then saves it`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val supabase = FakeSupabaseService(session = session(providers = listOf("email"), fresh = false))
            val viewModel = viewModel(profile, supabase)
            advanceUntilIdle()

            viewModel.onPasswordChange("new-secret")
            viewModel.save()
            advanceUntilIdle()
            assertEquals(
                ReauthPurpose.SAVE,
                viewModel.uiState.value.reauth
                    ?.purpose,
            )
            assertTrue(profile.saveSettingsCalls.isEmpty())

            viewModel.onReauthPasswordChange("old-secret")
            viewModel.submitReauth()
            advanceUntilIdle()

            assertEquals(listOf("old-secret"), supabase.reauthCalls)
            assertEquals("new-secret", profile.saveSettingsCalls.single().third)
        }

    @Test
    fun `going public needs a re-auth, going private does not`() =
        runTest {
            val stale = session(fresh = false)
            val goPublic = FakeProfileService(profile = profileRow(handle = "sam"))
            val vm1 = viewModel(goPublic, FakeSupabaseService(session = stale))
            advanceUntilIdle()
            vm1.onPublicProfileChange(true)
            vm1.save()
            advanceUntilIdle()
            assertEquals(
                ReauthPurpose.SAVE,
                vm1.uiState.value.reauth
                    ?.purpose,
            )
            assertTrue(goPublic.setPublicProfileCalls.isEmpty())

            val goPrivate = FakeProfileService(profile = profileRow(handle = "sam", publicProfile = true))
            val vm2 = viewModel(goPrivate, FakeSupabaseService(session = stale))
            advanceUntilIdle()
            vm2.onPublicProfileChange(false)
            vm2.save()
            advanceUntilIdle()
            assertNull(vm2.uiState.value.reauth)
            assertEquals(listOf(false), goPrivate.setPublicProfileCalls)
        }

    /**
     * The re-run after a server `reauth_required` reads the form again. The
     * password already landed on the first run, so it must not be re-sent:
     * GoTrue rejects an unchanged password (422 same_password) and the public
     * flag would never be written.
     */
    @Test
    fun `a server re-auth after the password landed re-runs the save without re-sending it`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow(handle = "sam"))
            val supabase = FakeSupabaseService(session = session(providers = listOf("email")))
            val viewModel = viewModel(profile, supabase)
            advanceUntilIdle()
            profile.setPublicProfileFailNext = postgrestException("reauth_required")

            viewModel.onPasswordChange("new-secret")
            viewModel.onPublicProfileChange(true)
            viewModel.save()
            advanceUntilIdle()
            assertEquals(
                ReauthPurpose.SAVE,
                viewModel.uiState.value.reauth
                    ?.purpose,
            )
            assertFalse(viewModel.uiState.value.didSaveFail)

            viewModel.onReauthPasswordChange("new-secret") // already the account's password
            viewModel.submitReauth()
            advanceUntilIdle()

            assertEquals(listOf("new-secret", null), profile.saveSettingsCalls.map { it.third })
            assertEquals(listOf(true, true), profile.setPublicProfileCalls)
            assertNull(viewModel.uiState.value.reauth)
            assertFalse(viewModel.uiState.value.didSaveFail)
        }

    // MARK: - Deletion

    @Test
    fun `deletion walks confirm to purge to sign-out`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val supabase = FakeSupabaseService(session = session())
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
            val supabase = FakeSupabaseService(session = session())
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

    // MARK: - Health-data consent (#972)

    private val grant = HealthDataConsent("1.4.0", OffsetDateTime.parse("2026-10-01T09:30:00Z"))

    @Test
    fun `the consent row shows the live health-data grant`() =
        runTest {
            val consents = FakeConsentService().apply { healthData = grant }
            val viewModel = viewModel(consents = consents)
            advanceUntilIdle()

            assertEquals(HealthConsentStatus.Given(grant), viewModel.uiState.value.healthConsent)
        }

    @Test
    fun `no grant on file reads as not recorded`() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(HealthConsentStatus.NotRecorded, viewModel.uiState.value.healthConsent)
        }

    @Test
    fun `a failed consent read leaves the rest of settings usable`() =
        runTest {
            val consents = FakeConsentService().apply { healthDataFailure = IOException("offline") }
            val viewModel = viewModel(consents = consents)
            advanceUntilIdle()

            assertEquals(HealthConsentStatus.Unavailable, viewModel.uiState.value.healthConsent)
            assertFalse(viewModel.uiState.value.isLoading)
            assertNull(viewModel.uiState.value.loadErrorRes)
        }

    @Test
    fun `withdrawing asks first, then deletes the account and signs out`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val supabase = FakeSupabaseService(session = session())
            val viewModel = viewModel(profile, supabase, consents = FakeConsentService().apply { healthData = grant })
            advanceUntilIdle()

            viewModel.requestWithdrawConsent()
            assertEquals(DeletionState.CONFIRMING_WITHDRAWAL, viewModel.uiState.value.deletion)
            assertEquals(0, profile.deleteAccountCount)

            viewModel.confirmDelete()
            advanceUntilIdle()

            assertEquals(1, profile.deleteAccountCount)
            assertEquals(1, supabase.signOutCount)
        }

    @Test
    fun `cancelling the withdrawal deletes nothing`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val viewModel = viewModel(profile, consents = FakeConsentService().apply { healthData = grant })
            advanceUntilIdle()

            viewModel.requestWithdrawConsent()
            viewModel.cancelDelete()
            advanceUntilIdle()

            assertEquals(DeletionState.IDLE, viewModel.uiState.value.deletion)
            assertEquals(0, profile.deleteAccountCount)
        }

    @Test
    fun `withdrawing with a stale sign-in re-auths before deleting`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val supabase = FakeSupabaseService(session = session(providers = listOf("email"), fresh = false))
            val viewModel = viewModel(profile, supabase, consents = FakeConsentService().apply { healthData = grant })
            advanceUntilIdle()

            viewModel.requestWithdrawConsent()
            viewModel.confirmDelete()
            advanceUntilIdle()
            assertEquals(DeletionState.REAUTHENTICATING, viewModel.uiState.value.deletion)
            assertEquals(0, profile.deleteAccountCount)

            viewModel.onReauthPasswordChange("hunter2")
            viewModel.submitReauth()
            advanceUntilIdle()

            assertEquals(1, profile.deleteAccountCount)
            assertEquals(1, supabase.signOutCount)
        }
}

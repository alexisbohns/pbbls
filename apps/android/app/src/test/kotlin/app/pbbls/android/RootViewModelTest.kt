package app.pbbls.android

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import app.pbbls.android.features.karma.AchievementMomentCard
import app.pbbls.android.features.karma.AchievementNotificationService
import app.pbbls.android.features.karma.KarmaNotificationService
import app.pbbls.android.testing.FakeSupabaseService
import app.pbbls.android.testing.MainDispatcherRule
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `RootViewModel`'s session→destination transitions and the parked-invite
 * lifecycle it takes over from `ConnectionsService.pendingInviteToken` (#852,
 * design §5, D8).
 *
 * Two tests pin behaviour the old parked-token field got subtly right: a
 * REAL sign-out drops a pending invite (it belonged to the session that just
 * ended), but a token parked by a cold-start App Link BEFORE auth ever
 * resolves must survive — `hasHadSession` is what tells the two apart, and
 * both read as `userId == null` otherwise (D12).
 */
class RootViewModelTest {
    @get:Rule
    val rule = MainDispatcherRule()

    /** A fresh, isolated [KarmaNotificationService] — RootViewModel's karma flash is not under test here. */
    private fun karma() = KarmaNotificationService(CoroutineScope(rule.dispatcher))

    /** A fresh, isolated [AchievementNotificationService] — a plain constructible class, no fake needed. */
    private fun achievements() = AchievementNotificationService()

    private fun card(slug: String) = AchievementMomentCard(slug = slug, record = null, karmaGranted = 0)

    private fun session(userId: String) =
        UserSession(
            accessToken = "token",
            refreshToken = "refresh",
            expiresIn = 3600,
            tokenType = "bearer",
            user = UserInfo(id = userId, aud = "authenticated"),
        )

    @Test
    fun `a resolved null session asks for the Welcome stack`() =
        runTest(rule.dispatcher) {
            val supabase = FakeSupabaseService()
            val vm = RootViewModel(supabase, karma(), achievements(), SavedStateHandle())

            supabase.emitResolved(session = null)
            advanceUntilIdle()

            assertEquals(RootDestination.SignedOut, vm.uiState.value.destination)
        }

    @Test
    fun `a resolved session asks for the Path stack`() =
        runTest(rule.dispatcher) {
            val supabase = FakeSupabaseService()
            val vm = RootViewModel(supabase, karma(), achievements(), SavedStateHandle())

            supabase.emitResolved(session = session(userId = "u1"))
            advanceUntilIdle()

            assertEquals(RootDestination.SignedIn, vm.uiState.value.destination)
        }

    @Test
    fun `an unresolved session asks for neither`() =
        runTest(rule.dispatcher) {
            val vm = RootViewModel(FakeSupabaseService(), karma(), achievements(), SavedStateHandle())

            advanceUntilIdle()

            assertEquals(RootDestination.Unresolved, vm.uiState.value.destination)
        }

    @Test
    fun `a pending invite survives a SavedStateHandle round trip`() =
        runTest(rule.dispatcher) {
            val handle = SavedStateHandle()
            val vm = RootViewModel(FakeSupabaseService(), karma(), achievements(), handle)

            vm.onInviteTokenReceived("tok-1")
            advanceUntilIdle()

            val restored = RootViewModel(FakeSupabaseService(), karma(), achievements(), handle)
            assertEquals("tok-1", restored.uiState.value.pendingInvite)
        }

    @Test
    fun `consuming the invite clears it so it cannot re-present`() =
        runTest(rule.dispatcher) {
            val vm = RootViewModel(FakeSupabaseService(), karma(), achievements(), SavedStateHandle())
            vm.onInviteTokenReceived("tok-1")
            advanceUntilIdle()

            vm.onInviteConsumed()
            advanceUntilIdle()

            assertNull(vm.uiState.value.pendingInvite)
        }

    @Test
    fun `signing out drops a pending invite from the old session`() =
        runTest(rule.dispatcher) {
            val supabase = FakeSupabaseService()
            val vm = RootViewModel(supabase, karma(), achievements(), SavedStateHandle())
            supabase.emitResolved(session = session(userId = "u1"))
            vm.onInviteTokenReceived("tok-1")
            advanceUntilIdle()

            supabase.emitResolved(session = null)
            advanceUntilIdle()

            assertNull(vm.uiState.value.pendingInvite)
        }

    @Test
    fun `a token parked before auth resolves is kept`() =
        runTest(rule.dispatcher) {
            // The cold-start App Link case (D12): the token arrives before there
            // has ever been a session, and must NOT be treated as a sign-out.
            val supabase = FakeSupabaseService()
            val vm = RootViewModel(supabase, karma(), achievements(), SavedStateHandle())

            vm.onInviteTokenReceived("tok-1")
            supabase.emitResolved(session = null)
            advanceUntilIdle()

            assertEquals("tok-1", vm.uiState.value.pendingInvite)
        }

    /**
     * `Snapshot.sendApplyNotifications()` is explicit after every direct
     * mutation of [achievementNotify] below — nothing drives the Recomposer's
     * frame clock in a JVM test, mirroring [FakeSupabaseService.emitResolved]
     * and `PathViewModelTest`'s `applyKarmaBalance` case.
     */
    @Test
    fun `a presented queue surfaces as a moment with the right position and total`() =
        runTest(rule.dispatcher) {
            val achievementNotify = achievements()
            val vm = RootViewModel(FakeSupabaseService(), karma(), achievementNotify, SavedStateHandle())

            achievementNotify.present(listOf(card("first-soul"), card("first-glyph")))
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            val moment = vm.uiState.value.achievementMoment
            assertEquals("first-soul", moment?.card?.slug)
            assertEquals(1, moment?.position)
            assertEquals(2, moment?.total)
            assertFalse(moment?.isLast ?: true)
        }

    @Test
    fun `advancing the achievement moment moves to the next card`() =
        runTest(rule.dispatcher) {
            val achievementNotify = achievements()
            val vm = RootViewModel(FakeSupabaseService(), karma(), achievementNotify, SavedStateHandle())
            achievementNotify.present(listOf(card("first-soul"), card("first-glyph")))
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            vm.onAchievementAdvanced()
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            val moment = vm.uiState.value.achievementMoment
            assertEquals("first-glyph", moment?.card?.slug)
            assertEquals(2, moment?.position)
            assertEquals(2, moment?.total)
            assertTrue(moment?.isLast ?: false)
        }

    @Test
    fun `dismissing the achievement moment clears it`() =
        runTest(rule.dispatcher) {
            val achievementNotify = achievements()
            val vm = RootViewModel(FakeSupabaseService(), karma(), achievementNotify, SavedStateHandle())
            achievementNotify.present(listOf(card("first-soul"), card("first-glyph")))
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            vm.onAchievementDismissed()
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            assertNull(vm.uiState.value.achievementMoment)
        }
}

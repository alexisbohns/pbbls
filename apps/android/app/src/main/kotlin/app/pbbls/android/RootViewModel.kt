package app.pbbls.android

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.core.data.AchievementMoment
import app.pbbls.android.core.data.AchievementNotificationService
import app.pbbls.android.core.data.KarmaEarnedContent
import app.pbbls.android.core.data.KarmaNotificationService
import app.pbbls.android.core.data.SupabaseServicing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val KEY_PENDING_INVITE = "root_pending_invite"

/**
 * Where [RootViewModel] wants the top-level stack, replacing `RootScreen`'s
 * `if (canShowAuthedTabs) … else …` composition branch (#852, design §5, D8).
 *
 * [Unresolved] is its own case rather than folded into [SignedOut] because the
 * system splash owns the launch hold on exactly this distinction —
 * `MainActivity` polls `isInitializing` to release it — so collapsing the two
 * would flash the signed-out funnel for a frame on every warm, already-signed-in
 * launch.
 */
sealed interface RootDestination {
    data object Unresolved : RootDestination

    data object SignedOut : RootDestination

    data object SignedIn : RootDestination
}

/**
 * [pendingInvite] is the App Link token today parked on
 * `ConnectionsService.pendingInviteToken` (deleted in #852). It lives
 * here instead so it survives process death via [SavedStateHandle] — a token
 * the server has not seen is exactly what that rule is for
 * (`apps/android/CLAUDE.md`).
 */
data class RootUiState(
    val destination: RootDestination = RootDestination.Unresolved,
    /** Mirrors [SupabaseServicing.session]'s user id — data only, never used to call the service. */
    val userId: String? = null,
    val pendingInvite: String? = null,
    /** The "+N karma" pastille content, or null when idle. Mirrors [KarmaNotificationService.activeCapsule]. */
    val karmaFlash: KarmaEarnedContent? = null,
    /** The unlock moment on screen, or null when idle. Mirrors [AchievementNotificationService.moment]. */
    val achievementMoment: AchievementMoment? = null,
)

/**
 * Owns the session→destination transition that lives in `RootScreen`'s
 * composition today (#852, design §5): `supabase.start()`, the
 * `hasHadSession`/`isInitializing` bookkeeping that tells "signed out" apart
 * from "auth has not resolved yet", and the parked-invite lifecycle (D8, D12).
 *
 * **Not yet consumed.** `RootScreen` still branches on `canShowAuthedTabs`
 * itself — `RootScreen` drives off [uiState] instead (#852). This class is
 * built and fully tested standalone first, which is why nothing below
 * constructs one yet.
 *
 * **`palettes.load()`, `referenceData.load()` and `snapUrls.invalidateAll()`
 * stay in `RootScreen`, not here.** Those three services are permanent
 * CompositionLocals (design §7, `apps/android/CLAUDE.md`'s "Three
 * CompositionLocals are permanent") — ambient reference data read by leaf
 * components, not navigation state — so they are not part of this migration.
 * Likewise the onboarding *gate* stays a pure `OnboardingGate.shouldPresent`
 * call at the `RootScreen` composition (it needs `OnboardingPreferences`'
 * `Context`-backed flag, which has no place in a plain-JVM-tested ViewModel);
 * only the session/invite state that actually drives the stack lives here.
 */
@HiltViewModel
class RootViewModel
    @Inject
    constructor(
        private val supabase: SupabaseServicing,
        private val karma: KarmaNotificationService,
        private val achievementNotify: AchievementNotificationService,
        private val savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow(
                RootUiState(pendingInvite = savedStateHandle[KEY_PENDING_INVITE]),
            )
        val uiState: StateFlow<RootUiState> = _uiState.asStateFlow()

        /**
         * Distinguishes "signed out" from "auth has not resolved yet" to
         * [onAuthState] — both read as `userId == null` otherwise. Ported
         * verbatim from `RootScreen` (design D12):
         *
         * A parked invite belongs to the session it arrived in. Dropping it on
         * the FIRST resolution — nobody has ever signed in this process — would
         * throw away a token parked by a cold-start App Link before auth
         * resolves. Dropping it on a REAL sign-out (a resolved session going to
         * null after having been signed in) is correct: it belonged to the
         * session that just ended, and would otherwise re-present on the next
         * sign-in, guard satisfied, showing the previous session's result.
         */
        private var hasHadSession = false

        init {
            // supabase.start() collects the auth-status stream for the app's
            // lifetime and suspends forever under normal operation (ported from
            // `RootScreen`'s `LaunchedEffect(Unit) { supabase.start() }`) — its
            // own coroutine so it never blocks the state collector below.
            viewModelScope.launch { supabase.start() }
            // This collects the Compose state `start()`'s own sessionStatus
            // collector maintains — it never touches supabase-kt itself, so the
            // "never call back into supabase-kt from inside the collector" rule
            // (apps/android/CLAUDE.md) does not apply here; it applies to
            // `SupabaseService.start()`'s collector, unchanged by this class.
            viewModelScope.launch {
                snapshotFlow { supabase.session?.user?.id to supabase.isInitializing }
                    .distinctUntilChanged()
                    .collect { (userId, isInitializing) -> onAuthState(userId, isInitializing) }
            }
            // Its own coroutine, deliberately: karma is unrelated to auth, and
            // folding it into the sessionStatus collector above would risk the
            // "never call back into supabase-kt from inside its own collector"
            // deadlock rule (apps/android/CLAUDE.md) the moment either grows.
            viewModelScope.launch {
                snapshotFlow { karma.activeCapsule }
                    .collect { flash -> _uiState.update { it.copy(karmaFlash = flash) } }
            }
            // Same reasoning, its own coroutine: the unlock queue is unrelated to
            // both auth and karma, and folding it into either collector above
            // risks the same deadlock rule the moment one of them grows.
            viewModelScope.launch {
                snapshotFlow { achievementNotify.moment }
                    .collect { moment -> _uiState.update { it.copy(achievementMoment = moment) } }
            }
        }

        private fun onAuthState(
            userId: String?,
            isInitializing: Boolean,
        ) {
            val destination =
                when {
                    isInitializing -> RootDestination.Unresolved
                    userId != null -> RootDestination.SignedIn
                    else -> RootDestination.SignedOut
                }
            if (!isInitializing && userId == null && hasHadSession) {
                clearPendingInvite()
            }
            // Only a resolved status updates hasHadSession — Unresolved must
            // never be mistaken for a session, real or absent.
            if (!isInitializing) hasHadSession = userId != null
            _uiState.update { it.copy(destination = destination, userId = userId) }
        }

        /**
         * Parks an invite App Link token (D8). Called from `MainActivity`'s
         * intent handling; harmless to call more than
         * once — the latest token wins.
         */
        fun onInviteTokenReceived(token: String) {
            savedStateHandle[KEY_PENDING_INVITE] = token
            _uiState.update { it.copy(pendingInvite = token) }
        }

        /** Consumed once the accept surface has been navigated to. */
        fun onInviteConsumed() = clearPendingInvite()

        /**
         * Signs out (#852) — moved off `RootScreen`'s own `LocalSupabaseService`
         * read, which was the last call through that local outside the three
         * permanent ambient-data ones (`apps/android/CLAUDE.md`).
         */
        fun onSignOut() {
            viewModelScope.launch { supabase.signOut() }
        }

        /** Tap-to-dismiss on the karma pastille (D9). */
        fun onKarmaDismissed() = karma.dismiss()

        /** Advances the unlock queue to its next card, ending the moment after the last one. */
        fun onAchievementAdvanced() = achievementNotify.advance()

        /** Tap-the-scrim or back-gesture dismissal — skips the rest of the unlock queue (D13). */
        fun onAchievementDismissed() = achievementNotify.dismiss()

        private fun clearPendingInvite() {
            savedStateHandle.remove<String>(KEY_PENDING_INVITE)
            _uiState.update { it.copy(pendingInvite = null) }
        }
    }

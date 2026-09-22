package app.pbbls.android.testing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import app.pbbls.android.core.data.SupabaseServicing
import io.github.jan.supabase.auth.user.UserSession

/**
 * In-memory [SupabaseServicing] (#848). See [PebblesTestHarness] for where these
 * live and why.
 *
 * Before #848 the auth surface was only reachable through the concrete
 * `SupabaseService`, whose client came from `AppEnvironment` and threw on a
 * blank `SUPABASE_URL`, so a test could not construct one at all.
 *
 * [session] and [isInitializing] are Compose state, like the real service's —
 * `RootViewModel` observes them through `snapshotFlow`, so a fake backing them
 * with plain `var`s would make it look correct in a test while never emitting
 * in the app (the same reasoning as `FakePathStatsService`). [isInitializing]
 * defaults to `true`, matching the real service: "no session yet" and "auth
 * has not resolved" must stay distinguishable, which is exactly what
 * `RootDestination.Unresolved` exists for.
 */
class FakeSupabaseService(
    session: UserSession? = null,
    isInitializing: Boolean = true,
) : SupabaseServicing {
    override var session: UserSession? by mutableStateOf(session)

    override var isInitializing: Boolean by mutableStateOf(isInitializing)

    var startCount = 0
        private set

    /** Every (email, password) pair passed to [signIn], oldest first. */
    val signInCalls = mutableListOf<Pair<String, String>>()

    /** Every (email, password) pair passed to [signUp], oldest first. */
    val signUpCalls = mutableListOf<Pair<String, String>>()

    var googleSignInCount = 0
        private set

    var signOutCount = 0
        private set

    private val armed = ArmedFailure()

    /** Thrown by the next call, then cleared. */
    var failNext: Exception?
        get() = armed.next
        set(value) {
            armed.next = value
        }

    /**
     * Returns immediately. The real [app.pbbls.android.core.data.SupabaseService.start]
     * collects `sessionStatus` and suspends forever — a fake that did the same
     * would hang every test that called it. Deliberately does NOT resolve
     * [isInitializing] itself: the real stream resolves on its first event, not
     * on `start()` merely being called, so a test drives resolution explicitly
     * with [emitResolved].
     */
    override suspend fun start() {
        startCount += 1
        armed.fire()
    }

    /**
     * Simulates the auth-status stream delivering a resolved event — the
     * `Authenticated`/`NotAuthenticated` branch of the real
     * `SupabaseService.start` collector. Sets [session] and [isInitializing]
     * together and pushes a snapshot apply so a `snapshotFlow` collector (e.g.
     * `RootViewModel`'s) picks it up immediately: nothing drives the Recomposer
     * frame clock in a JVM test, so the notification has to be explicit here,
     * the same reason `PathViewModelTest` calls
     * `Snapshot.sendApplyNotifications()` after mutating `FakePathStatsService`.
     */
    fun emitResolved(session: UserSession?) {
        this.session = session
        this.isInitializing = false
        Snapshot.sendApplyNotifications()
    }

    override suspend fun signIn(
        email: String,
        password: String,
    ) {
        signInCalls += email to password
        armed.fire()
    }

    override suspend fun signUp(
        email: String,
        password: String,
    ) {
        signUpCalls += email to password
        armed.fire()
    }

    override suspend fun signInWithGoogle() {
        googleSignInCount += 1
        armed.fire()
    }

    /**
     * Never throws, matching the real service: `signOut` catches and logs, because
     * the local token is wiped regardless. A signed-out user is the only
     * observable outcome, so there is no error path for a test to drive.
     */
    override suspend fun signOut() {
        signOutCount += 1
        session = null
    }
}

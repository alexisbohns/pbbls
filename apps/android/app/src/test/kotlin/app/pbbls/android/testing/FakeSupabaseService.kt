package app.pbbls.android.testing

import app.pbbls.android.services.SupabaseServicing
import io.github.jan.supabase.auth.user.UserSession

/**
 * In-memory [SupabaseServicing] (#848). See [PebblesTestHarness] for where these
 * live and why.
 *
 * Before #848 the auth surface was only reachable through the concrete
 * `SupabaseService`, whose client came from `AppEnvironment` and threw on a
 * blank `SUPABASE_URL`, so a test could not construct one at all.
 */
class FakeSupabaseService(
    override var session: UserSession? = null,
    override var isInitializing: Boolean = false,
) : SupabaseServicing {
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
     * Returns immediately. The real [app.pbbls.android.services.SupabaseService.start]
     * collects `sessionStatus` and suspends forever — a fake that did the same
     * would hang every test that called it.
     */
    override suspend fun start() {
        startCount += 1
        armed.fire()
        isInitializing = false
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

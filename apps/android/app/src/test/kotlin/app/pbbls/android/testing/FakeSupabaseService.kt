package app.pbbls.android.testing

import app.pbbls.android.services.SupabaseServicing
import io.github.jan.supabase.auth.user.UserSession

/**
 * In-memory [SupabaseServicing] for tests.
 *
 * It holds no `SupabaseClient` and reads no `BuildConfig` — that is the whole
 * point. Before #848 the auth surface was only reachable through the concrete
 * `SupabaseService`, whose client came from `AppEnvironment` and threw on a
 * blank `SUPABASE_URL`, so a test could not construct one at all.
 *
 * Lives in `src/test` because that is the only consumer until #857 lands
 * Robolectric; it moves to a real `core/testing` module with #851.
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

    /**
     * Thrown by the next call, then cleared — so one fake can drive a failure
     * and the retry that follows it without being rebuilt.
     */
    var failNext: Exception? = null

    override suspend fun start() {
        startCount += 1
        throwIfArmed()
        isInitializing = false
    }

    override suspend fun signIn(
        email: String,
        password: String,
    ) {
        signInCalls += email to password
        throwIfArmed()
    }

    override suspend fun signUp(
        email: String,
        password: String,
    ) {
        signUpCalls += email to password
        throwIfArmed()
    }

    override suspend fun signInWithGoogle() {
        googleSignInCount += 1
        throwIfArmed()
    }

    override suspend fun signOut() {
        signOutCount += 1
        throwIfArmed()
        session = null
    }

    private fun throwIfArmed() {
        val failure = failNext ?: return
        failNext = null
        throw failure
    }
}

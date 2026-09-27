package app.pbbls.android.core.data

import app.pbbls.android.testing.postgrestException
import app.pbbls.android.testing.testJwt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The client half of the recent sign-in check (#976). Must agree with the SQL
 * `recent_auth_ok` (20260927120000): any numeric amr `timestamp` within
 * [RecentAuth.WINDOW] counts; anything unparseable does not.
 */
class RecentAuthTest {
    private val now = Instant.parse("2026-09-27T12:00:00Z")

    /**
     * Real GoTrue payloads. The password `amr` array — `[{"method":"password",
     * "timestamp":1790539740}]` — was captured verbatim from a real sign-up
     * token by `verify-recent-auth.ts` against the linked project on
     * 2026-09-27; only the timestamp is substituted (via [at]) so the test
     * controls the clock. The `oauth` variant follows the same GoTrue shape
     * with `method` = `oauth`; it was not captured (no Google account in the
     * harness), but the shape is GoTrue's own and not surface-specific.
     */
    private object RealPayloads {
        fun password(at: Long) =
            """{"aud":"authenticated","role":"authenticated","aal":"aal1","amr":[{"method":"password","timestamp":$at}],"is_anonymous":false}"""

        fun oauth(at: Long) =
            """{"aud":"authenticated","role":"authenticated","aal":"aal1","amr":[{"method":"oauth","timestamp":$at}],"is_anonymous":false}"""
    }

    private fun tokenAt(
        secondsAgo: Long,
        payload: (Long) -> String = RealPayloads::password,
    ) = testJwt(payload(now.epochSecond - secondsAgo))

    @Test
    fun `a password sign-in a minute ago is fresh`() = assertTrue(RecentAuth.isFresh(tokenAt(60), now))

    @Test
    fun `an oauth sign-in a minute ago is fresh`() = assertTrue(RecentAuth.isFresh(tokenAt(60, RealPayloads::oauth), now))

    @Test
    fun `exactly at the window edge is fresh`() = assertTrue(RecentAuth.isFresh(tokenAt(600), now))

    @Test
    fun `eleven minutes ago is stale`() = assertFalse(RecentAuth.isFresh(tokenAt(660), now))

    @Test
    fun `any fresh stamp in a mixed amr counts`() {
        val old = now.epochSecond - 7200
        val fresh = now.epochSecond - 30
        val token = testJwt("""{"amr":[{"method":"password","timestamp":$old},{"method":"oauth","timestamp":$fresh}]}""")
        assertTrue(RecentAuth.isFresh(token, now))
    }

    @Test
    fun `missing, empty and malformed amr are never fresh`() {
        listOf(
            """{}""",
            """{"amr":[]}""",
            """{"amr":null}""",
            """{"amr":{"method":"password","timestamp":${now.epochSecond}}}""",
            """{"amr":[{"method":"password"}]}""",
            """{"amr":[{"method":"password","timestamp":"${now.epochSecond}"}]}""",
            """{"amr":[1,"x",null,{"timestamp":{"nested":1}}]}""",
        ).forEach { assertFalse(it, RecentAuth.isFresh(testJwt(it), now)) }
    }

    @Test
    fun `tokens that are not JWTs are never fresh`() {
        listOf(
            null,
            "",
            "token",
            "a.b",
            "a.%%%.c",
            "a.${"not json".encodeToByteArray().let {
                java.util.Base64
                    .getUrlEncoder()
                    .encodeToString(it)
            }}.c",
        ).forEach { assertFalse(it.toString(), RecentAuth.isFresh(it, now)) }
    }

    /**
     * The Google re-auth waits on this: `sessionStatus` replays the current
     * session, which can already look fresh after a server `reauth_required`.
     */
    @Test
    fun `a new sign-in needs a newer stamp than the one held before`() {
        val held = tokenAt(60)
        val before = RecentAuth.newestStamp(held)
        assertFalse(RecentAuth.isNewSignIn(held, before, now))
        assertTrue(RecentAuth.isNewSignIn(tokenAt(5), before, now))
        assertTrue(RecentAuth.isNewSignIn(tokenAt(5), null, now))
        assertFalse(RecentAuth.isNewSignIn(tokenAt(5), RecentAuth.newestStamp(tokenAt(1)), now))
        assertFalse(RecentAuth.isNewSignIn(tokenAt(660), RecentAuth.newestStamp(tokenAt(7200)), now))
    }

    @Test
    fun `reauth_required from PostgREST and from the edge function are both recognized`() {
        assertTrue(postgrestException("reauth_required").isReauthRequired())
        assertTrue(ReauthRequiredException().isReauthRequired())
        assertFalse(postgrestException("handle_taken").isReauthRequired())
        assertFalse(java.io.IOException("offline").isReauthRequired())
    }
}

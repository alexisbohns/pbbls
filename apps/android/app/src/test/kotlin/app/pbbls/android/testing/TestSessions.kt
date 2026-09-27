package app.pbbls.android.testing

import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession

/** A signed-in session for [userId] — the shape every fake auth test needs, and nothing else. */
fun testSession(userId: String = "user-1") =
    UserSession(
        accessToken = "token",
        refreshToken = "refresh",
        expiresIn = 3600,
        tokenType = "bearer",
        user = UserInfo(id = userId, aud = "authenticated"),
    )

/** An unsigned JWT around [payloadJson] — enough for code that only reads the payload. */
fun testJwt(payloadJson: String): String {
    val enc =
        java.util.Base64
            .getUrlEncoder()
            .withoutPadding()
    val header = enc.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
    val payload = enc.encodeToString(payloadJson.toByteArray())
    return "$header.$payload.signature"
}

/** An access token whose `amr` says the user signed in with a password at [at]. */
fun accessTokenSignedInAt(at: java.time.Instant): String =
    testJwt("""{"sub":"user-1","amr":[{"method":"password","timestamp":${at.epochSecond}}]}""")

/** A session for [userId] whose token proves a sign-in right now. */
fun freshSession(
    userId: String = "user-1",
    email: String? = "pebbler@example.com",
    identities: List<io.github.jan.supabase.auth.user.Identity>? = null,
) = UserSession(
    accessToken = accessTokenSignedInAt(java.time.Instant.now()),
    refreshToken = "refresh",
    expiresIn = 3600,
    tokenType = "bearer",
    user = UserInfo(id = userId, aud = "authenticated", email = email, identities = identities),
)

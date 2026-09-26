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

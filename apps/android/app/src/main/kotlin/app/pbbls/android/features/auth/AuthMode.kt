package app.pbbls.android.features.auth

import androidx.annotation.StringRes
import app.pbbls.android.R

/**
 * Login vs Sign-up mode for [AuthScreen] — the `AuthView.Mode` analog. [route] is
 * the NavHost path argument; [labelRes] the switcher label.
 */
enum class AuthMode(
    val route: String,
    @StringRes val labelRes: Int,
) {
    LOGIN("login", R.string.auth_mode_login),
    SIGNUP("signup", R.string.auth_mode_signup),
    ;

    companion object {
        /** Maps a NavHost `{mode}` argument back to a mode, defaulting to [LOGIN]. */
        fun fromRoute(route: String?): AuthMode = entries.firstOrNull { it.route == route } ?: LOGIN
    }
}

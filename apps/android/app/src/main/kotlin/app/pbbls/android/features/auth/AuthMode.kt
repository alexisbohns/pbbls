package app.pbbls.android.features.auth

import androidx.annotation.StringRes
import app.pbbls.android.R
import kotlinx.serialization.Serializable

/**
 * Login vs Sign-up mode for [AuthScreen] — the `AuthView.Mode` analog. [labelRes]
 * is the switcher label. [PebblesKey.Auth] carries this enum directly as a typed
 * `NavKey` field (#852) — `route`/`fromRoute` round-tripped it through a
 * NavHost path argument and are gone with `navigation-compose`; `AuthViewModel`
 * persists it across process death with `AuthMode.name`/`valueOf` instead.
 */
@Serializable
enum class AuthMode(
    @StringRes val labelRes: Int,
) {
    LOGIN(R.string.auth_mode_login),
    SIGNUP(R.string.auth_mode_signup),
}

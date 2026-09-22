package app.pbbls.android.features.auth

import androidx.annotation.StringRes
import app.pbbls.android.R
import app.pbbls.android.core.data.DataError
import app.pbbls.android.core.data.toDataError
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException

/**
 * A sign-in or sign-up failure as its inline string resource (#850).
 *
 * **What this replaces.** `AuthScreen` and `WelcomeScreen` set
 * `authError = e.message` — whatever supabase-kt, Ktor or GoTrue happened to
 * put there, rendered verbatim under the password field. Unlocalized, often
 * English-only, sometimes JSON, sometimes "Connection reset", and directly
 * against D9. It is also the first thing a new user sees when anything goes
 * wrong, which makes it the worst place in the app for raw SDK text.
 *
 * **Why `AuthErrorCode` and not the message.** GoTrue returns a stable machine
 * code and supabase-kt already parses it into an enum on
 * [AuthRestException.errorCode]. That is a real discriminator, unlike the
 * Postgres conditions elsewhere in this app which arrive as their message text
 * (see [DataError]). Matching the enum means a wording change upstream cannot
 * silently break the mapping.
 *
 * Anything unmapped falls to the generic line rather than to the SDK's words:
 * a user cannot act on `unexpected_failure` however it is phrased, and the log
 * keeps the cause.
 *
 * Pure, so it is unit-tested without a client.
 */
@StringRes
fun authErrorMessage(error: Throwable): Int {
    (error as? AuthRestException)?.let { return it.errorCode.toMessageRes() }
    return when (error.toDataError()) {
        DataError.Network -> R.string.error_offline
        DataError.Unauthorized -> R.string.auth_error_invalid_credentials
        else -> R.string.auth_error_generic
    }
}

@StringRes
private fun AuthErrorCode?.toMessageRes(): Int =
    when (this) {
        // The two a user can actually do something about.
        AuthErrorCode.InvalidCredentials -> R.string.auth_error_invalid_credentials
        AuthErrorCode.EmailExists, AuthErrorCode.UserAlreadyExists -> R.string.auth_error_email_exists

        AuthErrorCode.EmailNotConfirmed -> R.string.auth_error_email_not_confirmed
        AuthErrorCode.WeakPassword -> R.string.auth_error_weak_password
        AuthErrorCode.OverEmailSendRateLimit, AuthErrorCode.OverRequestRateLimit ->
            R.string.auth_error_rate_limited
        AuthErrorCode.UserBanned -> R.string.auth_error_user_banned
        AuthErrorCode.SignupDisabled, AuthErrorCode.EmailProviderDisabled ->
            R.string.auth_error_signup_disabled
        AuthErrorCode.ValidationFailed -> R.string.auth_error_invalid_email

        // Everything else, including null (a code this supabase-kt does not
        // know), is not something the user can act on.
        else -> R.string.auth_error_generic
    }

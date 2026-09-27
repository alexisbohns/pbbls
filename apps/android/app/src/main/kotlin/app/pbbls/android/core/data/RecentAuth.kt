package app.pbbls.android.core.data

import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.Duration
import java.time.Instant
import java.util.Base64

/**
 * The client half of the recent sign-in check (#976).
 *
 * GoTrue stamps every real authentication into the access token's `amr` claim
 * (`[{"method":"password","timestamp":<epoch s>}]`), and a refresh keeps the
 * stamps — so a fresh stamp means the credential was proven recently. The
 * server's `recent_auth_ok` (20260927120000) applies the same rule; this copy
 * only decides whether to PROMPT, the server stays the authority. [WINDOW] is
 * duplicated in that migration — change both together.
 *
 * The payload is read without verifying the signature, which is fine for a
 * UX decision about our own token and never a security one.
 */
object RecentAuth {
    val WINDOW: Duration = Duration.ofMinutes(10)

    fun isFresh(
        accessToken: String?,
        now: Instant = Instant.now(),
    ): Boolean {
        val amr = accessToken?.let(::payloadOf)?.get("amr") as? JsonArray ?: return false
        val cutoff = now.minus(WINDOW).epochSecond.toDouble()
        return amr.any { entry ->
            val stamp = (entry as? JsonObject)?.get("timestamp") as? JsonPrimitive
            val seconds = stamp?.takeUnless { it.isString }?.doubleOrNull
            seconds != null && seconds >= cutoff
        }
    }

    private fun payloadOf(token: String): JsonObject? {
        val encoded = token.split('.').getOrNull(1) ?: return null
        return runCatching {
            Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)) as? JsonObject
        }.getOrNull()
    }
}

/** The raised condition's text, shared by the SQL and the edge function. */
const val REAUTH_REQUIRED = "reauth_required"

/** delete-account answered 428: the session is live but its sign-in is too old. */
class ReauthRequiredException(
    cause: Throwable? = null,
) : Exception(REAUTH_REQUIRED, cause)

/** A provider re-auth came back as a DIFFERENT user; that session was signed out. */
class ReauthAccountMismatchException : Exception("reauth_account_mismatch")

/**
 * True for either shape of "sign in again": the edge function's 428 (already
 * mapped to [ReauthRequiredException] by `ProfileService`), or the profiles
 * trigger's `raise exception 'reauth_required'` surfaced by PostgREST.
 */
fun Throwable.isReauthRequired(): Boolean = this is ReauthRequiredException || (this is PostgrestRestException && error == REAUTH_REQUIRED)

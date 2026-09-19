package app.pbbls.android.services

import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * What went wrong, as a value the UI can switch on (#850).
 *
 * **The bug this replaces.** Domain errors were recovered by substring-matching
 * the exception's `message`, and that string is not what it looks like:
 * `RestException` builds `Exception.message` as
 *
 * ```
 * <error>\n<description>\nURL: <request url>\nHeaders: <headers>\nHttp Method: POST
 * ```
 *
 * so `e.message!!.contains("handle_taken")` was scanning the request URL and the
 * headers as well as the server's answer. supabase-kt already separates the
 * server's own words into [RestException.error] — the raw PostgREST `message`
 * field, and nothing else — so the fix is to match that, exactly, rather than to
 * search a blob.
 *
 * **Why the conditions arrive as text at all.** Every named condition in the
 * schema is raised as `raise exception 'handle_taken'`, i.e. the condition *is*
 * the Postgres message, and the SQLSTATE is `P0001` for all of them. There is no
 * second machine-readable field to prefer; [Conflict] therefore carries that
 * exact string, and each feature maps the ones it raises. An unrecognised one
 * stays a [Conflict] rather than becoming [Unknown], because the server did
 * answer — we just have no copy for it yet.
 *
 * `core/model` in the issue; it lives here because this app has no `core/`
 * module yet (#851 owns that split) and `services/` is where non-view code goes.
 */
sealed interface DataError {
    /** No answer from the server: offline, DNS, TLS, timeout. */
    data object Network : DataError

    /** 401/403 — a dead session, or RLS refusing the row. */
    data object Unauthorized : DataError

    data object NotFound : DataError

    /**
     * A named condition the server raised deliberately. [code] is the exact
     * `raise exception '…'` text, never a substring match.
     */
    data class Conflict(
        val code: String,
    ) : DataError

    /** A limit the product enforces, rather than a failure. */
    data object Quota : DataError

    /** Everything else, cause kept for the log — never for the user (D9). */
    data class Unknown(
        val cause: Throwable?,
    ) : DataError
}

/** The one named condition that is a product limit rather than a conflict. */
private const val MEDIA_QUOTA_EXCEEDED = "media_quota_exceeded"

/**
 * Classify from the fields a [RestException] carries, without the exception.
 *
 * Pure and therefore unit-testable: building a real `RestException` needs a Ktor
 * `HttpResponse`, which a JVM test cannot produce without pulling in a mock
 * engine. Splitting the decision out means the part with the judgement in it is
 * covered, and [toDataError] below is the trivial adapter.
 *
 * [error] is `RestException.error`, [sqlState] is `PostgrestRestException.code`.
 */
internal fun dataErrorOf(
    statusCode: Int,
    error: String,
    sqlState: String?,
): DataError =
    when {
        statusCode == 401 || statusCode == 403 || sqlState == "42501" -> DataError.Unauthorized
        statusCode == 404 -> DataError.NotFound
        error == MEDIA_QUOTA_EXCEEDED -> DataError.Quota
        // A raised condition: P0001 is what `raise exception` emits, and the
        // schema's conditions are lower_snake_case single tokens. Anything else
        // at 4xx is a request we built wrong, which is not a domain verdict.
        error.isNotBlank() && (sqlState == "P0001" || statusCode in 400..499) -> DataError.Conflict(error)
        else -> DataError.Unknown(null)
    }

/**
 * Classify a thrown exception.
 *
 * Rethrows [CancellationException] first, as everything on this path must:
 * mapping a cancellation to an error would report "something went wrong" for a
 * screen the user simply left.
 */
fun Throwable.toDataError(): DataError {
    if (this is CancellationException) throw this
    return when (this) {
        // HttpRequestException extends IOException, so this covers supabase-kt's
        // transport failures and raw socket ones alike.
        is IOException -> DataError.Network
        is PostgrestRestException -> dataErrorOf(statusCode, error, code)
        is RestException -> dataErrorOf(statusCode, error, null)
        else -> DataError.Unknown(this)
    }
}

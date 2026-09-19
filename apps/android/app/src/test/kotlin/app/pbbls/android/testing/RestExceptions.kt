package app.pbbls.android.testing

import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking

/**
 * Builds the real exception supabase-kt throws, for a JVM test (#849/#850).
 *
 * A `RestException` needs a Ktor `HttpResponse`, so a test that wants one has
 * three options: a stand-in exception, which `toDataError` correctly refuses to
 * classify as a server verdict and which therefore tests nothing; a mock HTTP
 * engine; or no coverage of the error path at all. This is the middle one, and
 * it is nine lines.
 *
 * The values mirror what PostgREST really returns for a raised condition —
 * `{"code":"P0001","message":"handle_taken"}` — which
 * `PostgrestImpl.parseErrorResponse` unpacks into `error` and `code`.
 */
fun postgrestException(
    condition: String,
    sqlState: String? = "P0001",
    status: HttpStatusCode = HttpStatusCode.BadRequest,
): PostgrestRestException =
    PostgrestRestException(
        message = condition,
        hint = null,
        details = null,
        code = sqlState,
        response = mockResponse(status),
    )

private fun mockResponse(status: HttpStatusCode): HttpResponse =
    runBlocking {
        HttpClient(MockEngine { respond(content = "", status = status, headers = headersOf()) })
            .get("https://project.supabase.co/rest/v1/rpc/set_handle")
    }

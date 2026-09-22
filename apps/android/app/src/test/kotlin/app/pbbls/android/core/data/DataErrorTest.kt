package app.pbbls.android.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * The decoding contract (#850), pinned against the payload shape PostgREST
 * really returns.
 *
 * **Where the fixtures come from.** A `raise exception 'handle_taken'` in a
 * migration reaches the client as the HTTP body
 * `{"code":"P0001","details":null,"hint":null,"message":"handle_taken"}`;
 * supabase-kt's `PostgrestImpl.parseErrorResponse` passes `body.message` into
 * `RestException.error` and `body.code` into `PostgrestRestException.code`
 * untouched. So `error` is the bare condition and `sqlState` is the SQLSTATE,
 * which is what [dataErrorOf] takes.
 *
 * It takes those two fields rather than the exception because building a real
 * `RestException` needs a Ktor `HttpResponse`; the adapter that unpacks one is
 * three lines with no judgement in it, and this covers the part that decides.
 */
class DataErrorTest {
    // MARK: - Named conditions the schema raises

    @Test
    fun `each named condition decodes to a Conflict carrying it verbatim`() {
        // Every `raise exception '<slug>'` in the migrations is P0001 and
        // arrives as the message. One case per feature that maps them.
        val conditions =
            listOf(
                "handle_taken",
                "handle_reserved",
                "invalid_handle",
                "insufficient_karma",
                "not_in_market",
                "already_owned",
                "cannot_buy_own",
                "cannot_accept_own_invite",
                "invite_expired",
                "invite_not_found",
            )
        for (condition in conditions) {
            assertEquals(
                "condition $condition",
                DataError.Conflict(condition),
                dataErrorOf(statusCode = 400, error = condition, sqlState = "P0001"),
            )
        }
    }

    /** The one named condition that is a product limit, not a conflict. */
    @Test
    fun `media_quota_exceeded decodes to Quota`() {
        assertEquals(DataError.Quota, dataErrorOf(400, "media_quota_exceeded", "P0001"))
    }

    /** `using errcode = '42501'` is how the schema says "not yours". */
    @Test
    fun `sqlstate 42501 decodes to Unauthorized whatever the message says`() {
        assertEquals(
            DataError.Unauthorized,
            dataErrorOf(403, "Glyph not usable by user: abc", "42501"),
        )
    }

    // MARK: - Transport

    @Test
    fun `401 and 403 decode to Unauthorized`() {
        assertEquals(DataError.Unauthorized, dataErrorOf(401, "JWT expired", null))
        assertEquals(DataError.Unauthorized, dataErrorOf(403, "forbidden", null))
    }

    @Test
    fun `404 decodes to NotFound`() {
        assertEquals(DataError.NotFound, dataErrorOf(404, "not found", null))
    }

    @Test
    fun `a 5xx is not a domain verdict`() {
        assertTrue(dataErrorOf(500, "internal error", null) is DataError.Unknown)
    }

    @Test
    fun `a blank error is never a Conflict`() {
        assertTrue(dataErrorOf(400, "", null) is DataError.Unknown)
    }

    // MARK: - The Throwable adapter

    @Test
    fun `an IOException is a network failure`() {
        // supabase-kt's HttpRequestException extends IOException, so this is
        // the one branch that covers both it and a raw socket error.
        assertEquals(DataError.Network, IOException("connection reset").toDataError())
    }

    @Test
    fun `an unrecognised throwable keeps its cause for the log`() {
        val boom = IllegalStateException("not authenticated")
        assertSame(boom, (boom.toDataError() as DataError.Unknown).cause)
    }

    /**
     * The rule the whole #849/#850 pair rests on: a cancellation is not an
     * error. Mapping one would report "something went wrong" for a screen the
     * user simply left, and would swallow the cancellation besides.
     */
    @Test(expected = CancellationException::class)
    fun `a CancellationException is rethrown, never classified`() {
        CancellationException("left the screen").toDataError()
    }
}

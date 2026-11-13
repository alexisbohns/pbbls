package app.pbbls.android.core.data

import app.pbbls.android.R
import app.pbbls.android.core.model.ReportReason
import app.pbbls.android.core.model.ReportTarget
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class ReportServiceTest {
    @Test
    fun `reason slugs are the server's, in the sheet's order`() {
        assertEquals(
            listOf("harassment", "hate", "impersonation", "sexual", "violence", "self_harm", "illegal", "spam", "other"),
            ReportReason.entries.map { it.slug },
        )
    }

    @Test
    fun `a glyph goes to report_content`() {
        val call = reportCall(ReportTarget.Glyph("g-1"), ReportReason.SPAM, detail = "x", removeAndBlock = true)
        assertEquals("report_content", call.function)
        assertEquals(JsonPrimitive("glyph"), call.params["p_target_kind"])
        assertEquals(JsonPrimitive("g-1"), call.params["p_target_id"])
        assertEquals(JsonPrimitive("spam"), call.params["p_reason"])
        assertEquals(JsonPrimitive("x"), call.params["p_detail"])
        assertFalse(call.params.containsKey("p_remove_and_block"))
    }

    @Test
    fun `a connection goes to report_person and carries the block`() {
        val call = reportCall(ReportTarget.Connection("c-1", "Ana"), ReportReason.HATE, detail = null, removeAndBlock = true)
        assertEquals("report_person", call.function)
        assertEquals(JsonPrimitive("c-1"), call.params["p_connection_id"])
        assertEquals(JsonPrimitive(true), call.params["p_remove_and_block"])
        assertFalse(call.params.containsKey("p_invite_token"))
        assertFalse(call.params.containsKey("p_detail"))
    }

    @Test
    fun `an invite never blocks`() {
        val call = reportCall(ReportTarget.Invite("tok", null), ReportReason.OTHER, detail = null, removeAndBlock = true)
        assertEquals("report_person", call.function)
        assertEquals(JsonPrimitive("tok"), call.params["p_invite_token"])
        assertEquals(JsonPrimitive(false), call.params["p_remove_and_block"])
        assertFalse(call.params.containsKey("p_connection_id"))
    }

    @Test
    fun `the receipt decodes the payload report_person returned on prod, verbatim`() {
        // packages/supabase/fixtures/report-person-receipt.json, copied byte for byte.
        val raw = requireNotNull(javaClass.classLoader?.getResource("report-person-receipt.json")).readText()
        val receipt = Json { ignoreUnknownKeys = true }.decodeFromString(ReportReceipt.serializer(), raw)
        assertEquals("8ce2b8e9-1a29-456c-86a1-eb0d3b27c10a", receipt.id)
        assertEquals("open", receipt.status)
        assertEquals(2026, receipt.createdAt.year)
        assertEquals(ZoneOffset.UTC, receipt.createdAt.offset)
    }

    @Test
    fun `the receipt tolerates an offset and fractional seconds`() {
        val raw = """{"id":"a","status":"open","created_at":"2026-11-12T20:54:43.123456+00:00"}"""
        val receipt = Json.decodeFromString(ReportReceipt.serializer(), raw)
        assertEquals(43, receipt.createdAt.second)
    }

    @Test
    fun `server conditions map to the three messages`() {
        assertEquals(R.string.report_error_unavailable, reportErrorMessage(DataError.Conflict("not_found")))
        // A bare 404 (a missing function, say) is not the server's `not_found` verdict.
        assertEquals(R.string.report_error_generic, reportErrorMessage(DataError.NotFound))
        assertEquals(R.string.report_error_own, reportErrorMessage(DataError.Conflict("cannot_report_own")))
        assertEquals(R.string.report_error_generic, reportErrorMessage(DataError.Conflict("invalid_reason")))
        assertEquals(R.string.report_error_generic, reportErrorMessage(DataError.Network))
        assertEquals(R.string.report_error_generic, reportErrorMessage(DataError.Unauthorized))
    }

    @Test
    fun `only a connection offers the block, and names fall back when blank`() {
        assertTrue(ReportTarget.Connection("c", "Ana").offersBlock)
        assertFalse(ReportTarget.Invite("t", "Ana").offersBlock)
        assertFalse(ReportTarget.Glyph("g").offersBlock)
        assertNull(ReportTarget.Connection("c", "").personName)
        assertEquals("Ana", ReportTarget.Invite("t", "Ana").personName)
    }
}

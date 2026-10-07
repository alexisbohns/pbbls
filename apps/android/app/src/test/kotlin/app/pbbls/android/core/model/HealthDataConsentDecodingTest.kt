package app.pbbls.android.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime

/**
 * `user_consents.granted_at` is written by every surface: `record_consent`
 * stamps `now()` (microseconds), and PostgREST emits it with a `+00:00`
 * offset. These are the shapes the Settings consent row must read (#972).
 */
class HealthDataConsentDecodingTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(grantedAt: String) =
        json.decodeFromString<List<HealthDataConsent>>(
            """[{"document_version":"1.4.0","granted_at":"$grantedAt"}]""",
        )

    @Test
    fun `reads PostgREST microsecond timestamps with a +00 offset`() {
        val row = decode("2026-10-07T08:12:45.123456+00:00").single()
        assertEquals("1.4.0", row.documentVersion)
        assertEquals(OffsetDateTime.parse("2026-10-07T08:12:45.123456Z").toInstant(), row.grantedAt.toInstant())
    }

    @Test
    fun `reads whole seconds and a Z suffix`() {
        assertEquals(
            OffsetDateTime.parse("2026-10-07T08:12:45Z").toInstant(),
            decode("2026-10-07T08:12:45Z").single().grantedAt.toInstant(),
        )
        assertEquals(
            OffsetDateTime.parse("2026-10-07T08:12:45Z").toInstant(),
            decode("2026-10-07T08:12:45+00:00").single().grantedAt.toInstant(),
        )
    }

    @Test
    fun `ignores the ledger columns it does not read`() {
        val payload =
            """[{"id":"7c1e","kind":"health_data","document_version":"1.3.0","source":"web_settings",""" +
                """"granted_at":"2026-09-12T10:00:00.5+00:00","withdrawn_at":null,"superseded_at":null}]"""
        assertEquals("1.3.0", json.decodeFromString<List<HealthDataConsent>>(payload).single().documentVersion)
    }
}

package app.pbbls.android.services

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the consent-metadata payload shape written into user metadata on
 * sign-up (the iOS `signUp` data block). Exercises the pure companion function
 * without constructing a live client.
 */
class ConsentMetadataTest {
    @Test
    fun containsExactlyBothConsentTimestamps() {
        val now = "2026-07-11T12:00:00Z"
        val payload = SupabaseService.consentMetadata(now)

        assertEquals(setOf("terms_accepted_at", "privacy_accepted_at"), payload.keys)
        assertEquals(now, payload["terms_accepted_at"]?.jsonPrimitive?.content)
        assertEquals(now, payload["privacy_accepted_at"]?.jsonPrimitive?.content)
    }
}

package app.pbbls.android.core.data

import app.pbbls.android.core.model.LegalVersions
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The sign-up metadata is a cross-surface contract: `handle_new_user`
 * (`packages/supabase/supabase/migrations/20260927090000_consent_terms_privacy.sql`)
 * reads these exact keys, and a key it does not read records nothing,
 * silently. [TRIGGER_KEYS] is copied from that function's `declare` block and
 * must move with it.
 */
class ConsentMetadataTest {
    private val now = "2026-07-11T12:00:00Z"
    private val payload = SupabaseService.consentMetadata(now)

    @Test
    fun `sends exactly the keys handle_new_user reads`() {
        assertEquals(TRIGGER_KEYS, payload.keys)
    }

    @Test
    fun `every act is stamped with the one sign-up instant`() {
        listOf("terms_accepted_at", "privacy_accepted_at", "health_data_consent_at", "age_attested_at")
            .forEach { assertEquals(it, now, payload[it]?.jsonPrimitive?.content) }
    }

    @Test
    fun `terms cite the terms version and every other act the privacy version`() {
        assertEquals(LegalVersions.TERMS, payload["terms_version"]?.jsonPrimitive?.content)
        assertEquals(LegalVersions.PRIVACY, payload["privacy_version"]?.jsonPrimitive?.content)
        assertEquals(LegalVersions.PRIVACY, payload["health_data_consent_version"]?.jsonPrimitive?.content)
        assertEquals(LegalVersions.PRIVACY, payload["age_attestation_version"]?.jsonPrimitive?.content)
    }

    /**
     * Without it the trigger's closed `case` falls back to `web_register`,
     * stamping an Android signup as web: silently, and uncorrectably.
     */
    @Test
    fun `declares the android surface`() {
        assertEquals("android", payload["signup_surface"]?.jsonPrimitive?.content)
    }

    /** Whole seconds, per the cross-surface timestamp rule (root CLAUDE.md). */
    @Test
    fun `the sign-up instant is whole seconds`() {
        val stamp = SupabaseService.signupInstant(Instant.parse("2026-09-27T10:00:00.987654321Z"))
        assertEquals("2026-09-27T10:00:00Z", stamp)
        assertTrue(!stamp.contains('.'))
    }

    private companion object {
        val TRIGGER_KEYS =
            setOf(
                "terms_accepted_at",
                "terms_version",
                "privacy_accepted_at",
                "privacy_version",
                "health_data_consent_at",
                "health_data_consent_version",
                "age_attested_at",
                "age_attestation_version",
                "signup_surface",
            )
    }
}

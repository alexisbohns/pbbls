package app.pbbls.android.features.consent

import app.pbbls.android.core.model.ActiveConsent
import app.pbbls.android.core.model.ConsentKind
import app.pbbls.android.core.model.LegalVersions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentGateLogicTest {
    private fun current() = ConsentGateLogic.REQUIRED.map { ActiveConsent(it.wire, ConsentGateLogic.versionFor(it)) }

    @Test
    fun `an empty ledger is missing all four, in display order`() {
        assertEquals(
            listOf(ConsentKind.TERMS, ConsentKind.PRIVACY, ConsentKind.HEALTH_DATA, ConsentKind.AGE_ASSURANCE),
            ConsentGateLogic.missing(emptyList()),
        )
    }

    @Test
    fun `a ledger at the current versions is missing nothing`() {
        assertEquals(emptyList<ConsentKind>(), ConsentGateLogic.missing(current()))
    }

    @Test
    fun `an older version counts as missing`() {
        val active = current().map { if (it.kind == "terms") it.copy(documentVersion = "1.0.9") else it }
        assertEquals(listOf(ConsentKind.TERMS), ConsentGateLogic.missing(active))
    }

    /** Design D8: a newer web-recorded version must never be downgraded by an older app. */
    @Test
    fun `a newer version satisfies`() {
        val active = current().map { if (it.kind == "privacy") it.copy(documentVersion = "1.10.0") else it }
        assertEquals(emptyList<ConsentKind>(), ConsentGateLogic.missing(active))
    }

    /** Rows web writes that the gate does not ask for (public_profile) are ignored, not an error. */
    @Test
    fun `unrelated kinds are ignored`() {
        val active = current() + ActiveConsent("public_profile", "1.1.0")
        assertEquals(emptyList<ConsentKind>(), ConsentGateLogic.missing(active))
    }

    @Test
    fun `health and age cite the privacy version, terms the terms version`() {
        assertEquals(LegalVersions.TERMS, ConsentGateLogic.versionFor(ConsentKind.TERMS))
        assertEquals(LegalVersions.PRIVACY, ConsentGateLogic.versionFor(ConsentKind.PRIVACY))
        assertEquals(LegalVersions.PRIVACY, ConsentGateLogic.versionFor(ConsentKind.HEALTH_DATA))
        assertEquals(LegalVersions.PRIVACY, ConsentGateLogic.versionFor(ConsentKind.AGE_ASSURANCE))
    }

    @Test
    fun `semver compares numerically, not lexically`() {
        assertTrue(ConsentGateLogic.isAtLeast("1.10.0", "1.9.0"))
        assertTrue(ConsentGateLogic.isAtLeast("1.3.0", "1.3.0"))
        assertFalse(ConsentGateLogic.isAtLeast("1.2.9", "1.3.0"))
        assertFalse(ConsentGateLogic.isAtLeast("not-a-version", "1.3.0"))
    }

    @Test
    fun `the fingerprint changes when any version changes`() {
        assertEquals("terms@1.2.0,privacy@1.4.0,health_data@1.4.0,age_assurance@1.4.0", ConsentGateLogic.fingerprint())
    }

    @Test
    fun `the gate records where consent was given, not how the account signed up`() {
        assertEquals("android_settings", ConsentGateLogic.SOURCE)
    }
}

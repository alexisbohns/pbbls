package app.pbbls.android.features.karma

import app.pbbls.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the compile-checked [KarmaReason] → `@StringRes` label mapping (D10):
 * only the two native flash reasons exist, each wired to its own non-zero,
 * distinct string resource (both covered by `LocalizationParityTest`).
 */
class KarmaReasonTest {
    @Test
    fun `only the two native flash reasons exist`() {
        assertEquals(
            listOf(KarmaReason.PEBBLE_CREATED, KarmaReason.PEBBLE_ENRICHED),
            KarmaReason.entries.toList(),
        )
    }

    @Test
    fun `each reason maps to its own label resource`() {
        assertEquals(R.string.karma_reason_pebble_created, KarmaReason.PEBBLE_CREATED.labelRes)
        assertEquals(R.string.karma_reason_pebble_enriched, KarmaReason.PEBBLE_ENRICHED.labelRes)
    }

    @Test
    fun `every reason has a non-zero, distinct label resource`() {
        val ids = KarmaReason.entries.map { it.labelRes }
        assertTrue("label resources must be non-zero", ids.all { it != 0 })
        assertEquals("label resources must be distinct", ids.size, ids.toSet().size)
    }
}

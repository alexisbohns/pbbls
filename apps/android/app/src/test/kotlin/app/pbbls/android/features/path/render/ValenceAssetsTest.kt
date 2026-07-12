package app.pbbls.android.features.path.render

import app.pbbls.android.features.path.models.ValencePolarity
import app.pbbls.android.features.path.models.ValenceSizeGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the `(size, polarity)` → `res/raw` mapping in [ValenceAssets] (C §4):
 * all nine valence shapes must resolve to a distinct, non-zero raw resource, so
 * a typo in the `valence_<size>_<polarity>` names fails the build rather than
 * silently painting the wrong shape. `R.raw.*` ids resolve on the unit-test
 * classpath (same as `KarmaReasonTest`'s `R.string.*` assertions).
 */
class ValenceAssetsTest {
    private val allPairs =
        ValenceSizeGroup.entries.flatMap { size ->
            ValencePolarity.entries.map { polarity -> size to polarity }
        }

    @Test
    fun `covers all nine size-polarity combinations`() {
        assertEquals(9, allPairs.size)
    }

    @Test
    fun `every combination resolves to a distinct, non-zero raw resource`() {
        val ids = allPairs.map { (size, polarity) -> ValenceAssets.resId(size, polarity) }
        assertTrue("all raw ids must be non-zero", ids.all { it != 0 })
        assertEquals("all nine raw ids must be distinct", 9, ids.toSet().size)
    }
}

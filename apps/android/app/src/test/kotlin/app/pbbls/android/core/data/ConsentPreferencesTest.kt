package app.pbbls.android.core.data

import app.pbbls.android.testing.InMemoryPrefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentPreferencesTest {
    private val prefs = ConsentPreferences(InMemoryPrefs())

    @Test
    fun `nothing is cached at first`() {
        assertFalse(prefs.isSatisfied("user-1", "fp-1"))
    }

    @Test
    fun `a pass is cached for that user and that fingerprint only`() {
        prefs.markSatisfied("user-1", "fp-1")

        assertTrue(prefs.isSatisfied("user-1", "fp-1"))
        assertFalse(prefs.isSatisfied("user-2", "fp-1"))
        assertFalse(prefs.isSatisfied("user-1", "fp-2"))
    }

    /** One entry, overwritten: the device keeps no list of every account that signed in on it. */
    @Test
    fun `a second user's pass replaces the first`() {
        prefs.markSatisfied("user-1", "fp-1")
        prefs.markSatisfied("user-2", "fp-1")

        assertFalse(prefs.isSatisfied("user-1", "fp-1"))
        assertTrue(prefs.isSatisfied("user-2", "fp-1"))
    }
}

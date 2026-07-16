package app.pbbls.android.features.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [settingsIsDirty] mirrors `SettingsSheet.isDirty`, and [linkedProviders]
 * mirrors its providers mapping (brand labels; the implicit `email` identity
 * is not a provider).
 */
class SettingsLogicTest {
    @Test
    fun `pristine form is not dirty`() {
        assertFalse(settingsIsDirty("Alexis", "Alexis", "g1", null, ""))
    }

    @Test
    fun `a changed non-empty name is dirty, whitespace-insensitively`() {
        assertTrue(settingsIsDirty("Alexis", "Sam", "g1", null, ""))
        assertTrue(settingsIsDirty("Alexis", "  Sam  ", "g1", null, ""))
        assertFalse(settingsIsDirty("Alexis", "  Alexis  ", "g1", null, ""))
    }

    @Test
    fun `clearing the name to empty is not dirty`() {
        assertFalse(settingsIsDirty("Alexis", "   ", "g1", null, ""))
    }

    @Test
    fun `a different picked glyph is dirty, the same one is not`() {
        assertTrue(settingsIsDirty("Alexis", "Alexis", "g1", "g2", ""))
        assertFalse(settingsIsDirty("Alexis", "Alexis", "g1", "g1", ""))
        assertTrue(settingsIsDirty("Alexis", "Alexis", null, "g1", ""))
    }

    @Test
    fun `a non-empty password is dirty`() {
        assertTrue(settingsIsDirty("Alexis", "Alexis", "g1", null, "hunter2"))
    }

    @Test
    fun `providers map to brand labels and drop email`() {
        assertEquals(listOf("Apple", "Google"), linkedProviders(listOf("apple", "google", "email")))
        assertEquals(emptyList<String>(), linkedProviders(listOf("email")))
        assertEquals(emptyList<String>(), linkedProviders(null))
    }
}

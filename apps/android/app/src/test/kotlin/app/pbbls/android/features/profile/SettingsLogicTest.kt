package app.pbbls.android.features.profile

import app.pbbls.android.R
import app.pbbls.android.services.DataError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    /** Claiming, releasing, and case-only edits, with the other fields pristine. */
    private fun handleDirty(
        initialHandle: String?,
        handle: String,
    ): Boolean = settingsIsDirty("A", "A", null, null, "", initialHandle, handle)

    @Test
    fun `a changed handle is dirty, compared normalized like the DB stores it`() {
        assertTrue(handleDirty(initialHandle = null, handle = "sam"))
        // Same handle in a different case is not a change — the RPC would store
        // the identical normalized value.
        assertFalse(handleDirty(initialHandle = "sam", handle = "  SAM  "))
        // Clearing the field is the release path, which is a change.
        assertTrue(handleDirty(initialHandle = "sam", handle = ""))
        // No handle before, still none: not dirty.
        assertFalse(handleDirty(initialHandle = null, handle = "   "))
    }

    @Test
    fun `a flipped public-profile toggle is dirty`() {
        assertTrue(
            settingsIsDirty(
                initialName = "A",
                name = "A",
                initialGlyphId = null,
                pickedGlyphId = null,
                newPassword = "",
                initialHandle = "sam",
                handle = "sam",
                initialPublicProfile = false,
                isPublicProfile = true,
            ),
        )
        assertFalse(
            settingsIsDirty(
                initialName = "A",
                name = "A",
                initialGlyphId = null,
                pickedGlyphId = null,
                newPassword = "",
                initialHandle = "sam",
                handle = "sam",
                initialPublicProfile = true,
                isPublicProfile = true,
            ),
        )
    }

    @Test
    fun `set_handle conditions map to inline errors, everything else falls through`() {
        assertEquals(
            R.string.settings_handle_error_taken,
            handleErrorStringRes(DataError.Conflict("handle_taken")),
        )
        assertEquals(
            R.string.settings_handle_error_reserved,
            handleErrorStringRes(DataError.Conflict("handle_reserved")),
        )
        assertEquals(
            R.string.settings_handle_error_invalid,
            handleErrorStringRes(DataError.Conflict("invalid_handle")),
        )
        // A condition the RPC does raise, but not about the handle.
        assertNull(handleErrorStringRes(DataError.Conflict("not_found")))
        // Transport failures are not handle verdicts. Before #850 these were
        // distinguished only by not happening to contain one of the slugs —
        // a `not_found` reply whose URL contained "invalid_handle" would have
        // been shown as an invalid handle.
        assertNull(handleErrorStringRes(DataError.Network))
        assertNull(handleErrorStringRes(DataError.NotFound))
        assertNull(handleErrorStringRes(DataError.Unauthorized))
        assertNull(handleErrorStringRes(DataError.Unknown(null)))
    }

    @Test
    fun `providers map to brand labels and drop email`() {
        assertEquals(listOf("Apple", "Google"), linkedProviders(listOf("apple", "google", "email")))
        assertEquals(emptyList<String>(), linkedProviders(listOf("email")))
        assertEquals(emptyList<String>(), linkedProviders(null))
    }
}

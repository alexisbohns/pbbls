package app.pbbls.android.core.data

import app.pbbls.android.testing.InMemoryPrefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearancePreferencesTest {
    @Test
    fun `wallpaper colours default to on`() {
        assertTrue(AppearancePreferences(InMemoryPrefs()).useWallpaperColors)
    }

    @Test
    fun `a write persists and survives a new instance`() {
        val prefs = InMemoryPrefs()
        AppearancePreferences(prefs).setUseWallpaperColors(false)
        assertFalse(AppearancePreferences(prefs).useWallpaperColors)
    }
}

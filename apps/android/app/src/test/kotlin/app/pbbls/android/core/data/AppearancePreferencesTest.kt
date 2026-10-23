package app.pbbls.android.core.data

import app.pbbls.android.testing.InMemoryPreferencesStore
import app.pbbls.android.testing.inMemorySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The three settings on [SettingsStore] (#853, #1207): defaults, and a write that persists. */
class AppearancePreferencesTest {
    @Test
    fun `wallpaper colours default to on`() {
        assertTrue(AppearancePreferences(inMemorySettings()).useWallpaperColors)
    }

    @Test
    fun `a write persists and survives a new instance`() {
        val store = InMemoryPreferencesStore()
        AppearancePreferences(inMemorySettings(store)).setUseWallpaperColors(false)
        assertFalse(AppearancePreferences(inMemorySettings(store)).useWallpaperColors)
    }

    @Test
    fun `onboarding shows until it is marked seen`() {
        val store = InMemoryPreferencesStore()
        val onboarding = OnboardingPreferences(inMemorySettings(store))
        assertFalse(onboarding.hasSeenOnboarding)
        onboarding.setHasSeenOnboarding(true)
        assertTrue("the write shows at once", onboarding.hasSeenOnboarding)
        assertTrue(OnboardingPreferences(inMemorySettings(store)).hasSeenOnboarding)
    }

    @Test
    fun `the day view defaults to the circle and remembers the calendar`() {
        val store = InMemoryPreferencesStore()
        assertEquals(DayView.CIRCLE, DisplayPreferences(inMemorySettings(store)).dayView)
        DisplayPreferences(inMemorySettings(store)).setDayView(DayView.CALENDAR)
        assertEquals(DayView.CALENDAR, DisplayPreferences(inMemorySettings(store)).dayView)
    }
}

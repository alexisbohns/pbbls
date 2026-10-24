package app.pbbls.android.core.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-local appearance settings (#853), on [SettingsStore] since #1207. Compose state, so the theme
 * recomposes the moment the Settings switch flips; no restart.
 */
@Singleton
class AppearancePreferences
    @Inject
    constructor(
        private val settings: SettingsStore,
    ) {
        /** Wallpaper (dynamic) colour. On by default (#853, maintainer decision). */
        val useWallpaperColors: Boolean get() = settings[KEY] ?: true

        fun setUseWallpaperColors(value: Boolean) = settings.set(KEY, value)

        private companion object {
            /** The key it had in `pebbles_prefs`, so the migration carries it over. */
            val KEY = booleanPreferencesKey("useWallpaperColors")
        }
    }

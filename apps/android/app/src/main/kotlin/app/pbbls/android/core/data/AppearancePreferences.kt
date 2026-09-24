package app.pbbls.android.core.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-local appearance settings (#853). Compose state, so the theme
 * recomposes the moment the Settings switch flips — no restart.
 *
 * SharedPreferences, per D5. This is the first real user setting, which is the
 * trigger D5 named for DataStore; one boolean does not pay for that migration,
 * and the decision log leaves the call to #858 or the next setting.
 */
@Singleton
class AppearancePreferences internal constructor(
    private val prefs: SharedPreferences,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    private var wallpaperColorsState by mutableStateOf(prefs.getBoolean(KEY_USE_WALLPAPER_COLORS, true))

    /** Wallpaper (dynamic) colour. On by default (#853, maintainer decision). */
    val useWallpaperColors: Boolean get() = wallpaperColorsState

    fun setUseWallpaperColors(value: Boolean) {
        wallpaperColorsState = value
        prefs.edit { putBoolean(KEY_USE_WALLPAPER_COLORS, value) }
    }

    private companion object {
        /** Shared with OnboardingPreferences and ComposerSnapshotStore: one prefs file for the app. */
        const val PREFS_NAME = "pebbles_prefs"
        const val KEY_USE_WALLPAPER_COLORS = "useWallpaperColors"
    }
}

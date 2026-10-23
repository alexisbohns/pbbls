package app.pbbls.android.core.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `hasSeenOnboarding`, the `@AppStorage("hasSeenOnboarding")` analog, on [SettingsStore] since #1207.
 * The onboarding overlay reads it at the session gate and sets it when the flow completes, so it
 * shows once and never again across restarts.
 */
@Singleton
class OnboardingPreferences
    @Inject
    constructor(
        private val settings: SettingsStore,
    ) {
        val hasSeenOnboarding: Boolean get() = settings[KEY] ?: false

        /**
         * False until the settings' first read. The splash hides the screen but not the composition, so
         * the gate must not decide on the default: a pushed onboarding entry stays pushed.
         */
        val isLoaded: Boolean get() = settings.isLoaded

        fun setHasSeenOnboarding(value: Boolean) = settings.set(KEY, value)

        private companion object {
            /** The key it had in `pebbles_prefs`, so the migration carries it over. */
            val KEY = booleanPreferencesKey("hasSeenOnboarding")
        }
    }

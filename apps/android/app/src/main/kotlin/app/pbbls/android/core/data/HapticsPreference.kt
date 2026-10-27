package app.pbbls.android.core.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pebbles' own haptics switch (#1242 D23, the #1241 decision of 2026-10-26), kept on this device: every
 * vibration the app plays asks it first. On until turned off; saved as it is switched. On [SettingsStore],
 * so it is Compose state: a read at the moment of a vibration sees a change made in Settings, with no restart.
 */
@Singleton
class HapticsPreference
    @Inject
    constructor(
        private val settings: SettingsStore,
    ) {
        val isOn: Boolean get() = settings[ENABLED] ?: true

        fun setOn(value: Boolean) = settings.set(ENABLED, value)

        private companion object {
            /** iOS's `HapticsPreference.storageKey`. */
            val ENABLED = booleanPreferencesKey("haptics.enabled")
        }
    }

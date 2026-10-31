package app.pbbls.android.core.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton

/** How Home's quick actions are laid out (iOS `HomeShortcutsLayout`). [raw] is what is stored: never rename it. */
enum class HomeShortcutsLayout(
    val raw: String,
) {
    ROW("row"),
    BENTO("bento"),
}

/**
 * The three Home shortcuts, in their fixed order (iOS `HomeShortcut`). [raw] is stored as the last
 * used one: never rename it. Icons and labels live with the composable in `features/home`.
 */
enum class HomeShortcut(
    val raw: String,
) {
    CAMERA("camera"),
    QUICK("quick"),
    PEBBLE("pebble"),
}

/**
 * Home's quick actions settings, kept on this device (#1383, spec D6; iOS `HomeShortcutsPreference`):
 * a row or a bento, pebble shapes or plain tiles, and the shortcut used last, which takes the
 * bento's square. In `core/data` because Settings (`features/profile`) writes it and Home reads it.
 * On [SettingsStore], so each read is Compose state and follows a change at once. An unknown stored
 * value reads as the default.
 */
@Singleton
class HomeShortcutsPreference
    @Inject
    constructor(
        private val settings: SettingsStore,
    ) {
        /** The pebble-shaped row by default. */
        val layout: HomeShortcutsLayout
            get() = HomeShortcutsLayout.entries.firstOrNull { it.raw == settings[LAYOUT] } ?: HomeShortcutsLayout.ROW

        val stones: Boolean get() = settings[STONES] ?: true

        val lastUsed: HomeShortcut
            get() = HomeShortcut.entries.firstOrNull { it.raw == settings[LAST_USED] } ?: HomeShortcut.CAMERA

        fun setLayout(value: HomeShortcutsLayout) = settings.set(LAYOUT, value.raw)

        fun setStones(value: Boolean) = settings.set(STONES, value)

        fun setLastUsed(value: HomeShortcut) = settings.set(LAST_USED, value.raw)

        private companion object {
            /** iOS's `HomeShortcutsPreference` keys, so the two surfaces read alike. */
            val LAYOUT = stringPreferencesKey("home.shortcuts.layout")
            val STONES = booleanPreferencesKey("home.shortcuts.stones")
            val LAST_USED = stringPreferencesKey("home.shortcuts.lastUsed")
        }
    }

package app.pbbls.android.features.home.shortcuts

import app.pbbls.android.core.data.HomeShortcut
import app.pbbls.android.core.data.HomeShortcutsLayout

/**
 * How Home draws its quick actions (#1383 D6): the three device settings of
 * `HomeShortcutsPreference`, read together. The defaults are the preference's: a pebble-shaped
 * row, the camera last used.
 */
data class HomeShortcutsStyle(
    val layout: HomeShortcutsLayout = HomeShortcutsLayout.ROW,
    val stones: Boolean = true,
    val lastUsed: HomeShortcut = HomeShortcut.CAMERA,
)

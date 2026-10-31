package app.pbbls.android.features.home.shortcuts

import androidx.annotation.StringRes
import app.pbbls.android.R
import app.pbbls.android.core.data.HomeShortcut
import app.pbbls.android.core.designsystem.PbblsIcon

/** What each Home shortcut draws (iOS `HomeShortcut.icon`). The enum itself lives in `core/data`. */
val HomeShortcut.icon: PbblsIcon
    get() =
        when (this) {
            HomeShortcut.CAMERA -> PbblsIcon.CAMERA
            HomeShortcut.QUICK -> PbblsIcon.PEBBLE_FLAT
            HomeShortcut.PEBBLE -> PbblsIcon.PLUS_CIRCLE
        }

/** Each shortcut's label, also its button's spoken name (iOS `HomeShortcut.title`). */
@get:StringRes
val HomeShortcut.labelRes: Int
    get() =
        when (this) {
            HomeShortcut.CAMERA -> R.string.home_shortcut_camera
            HomeShortcut.QUICK -> R.string.home_shortcut_quick
            HomeShortcut.PEBBLE -> R.string.home_shortcut_pebble
        }

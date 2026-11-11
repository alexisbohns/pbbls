package app.pbbls.android.features.home.shortcuts

import androidx.annotation.StringRes
import app.pbbls.android.R
import app.pbbls.android.core.data.HomeShortcut
import app.pbbls.android.core.designsystem.PbblsGlyph

/** What each Home shortcut draws (iOS `HomeShortcut.icon`). The enum itself lives in `core/data`. */
val HomeShortcut.icon: PbblsGlyph
    get() =
        when (this) {
            HomeShortcut.CAMERA -> PbblsGlyph.CAMERA
            HomeShortcut.QUICK -> PbblsGlyph.PEBBLE_FLAT
            HomeShortcut.PEBBLE -> PbblsGlyph.PLUS_CIRCLE
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

package app.pbbls.android.features.path.models

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.pbbls.android.R

/**
 * Per-grade UI mapping (M51) — mirrors iOS `Visibility.systemImageName` /
 * `Visibility.label`. Kept off the enum so the serializable model stays pure.
 */
@get:DrawableRes
val Visibility.iconRes: Int
    get() =
        when (this) {
            Visibility.SECRET -> R.drawable.ic_lock
            Visibility.PRIVATE -> R.drawable.ic_people
            Visibility.PUBLIC -> R.drawable.ic_globe
        }

@get:StringRes
val Visibility.labelRes: Int
    get() =
        when (this) {
            Visibility.SECRET -> R.string.visibility_secret
            Visibility.PRIVATE -> R.string.visibility_private
            Visibility.PUBLIC -> R.string.visibility_public
        }

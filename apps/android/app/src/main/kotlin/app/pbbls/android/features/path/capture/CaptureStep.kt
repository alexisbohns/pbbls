package app.pbbls.android.features.path.capture

import androidx.annotation.StringRes
import app.pbbls.android.R

/**
 * Capture (spec §6): photo before when, so the date arrives seeded from the
 * photo's EXIF (M58 D7); name last, against the save buttons.
 */
enum class CaptureStep(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int? = null,
    val isOptional: Boolean = false,
) {
    PHOTO(R.string.record_photo_title, R.string.record_photo_subtitle, isOptional = true),
    WHEN(R.string.record_when_title),
    NAME(R.string.record_name_title, R.string.capture_name_subtitle),
    ;

    val next: CaptureStep? get() = entries.getOrNull(ordinal + 1)
    val previous: CaptureStep? get() = if (ordinal == 0) null else entries[ordinal - 1]
}

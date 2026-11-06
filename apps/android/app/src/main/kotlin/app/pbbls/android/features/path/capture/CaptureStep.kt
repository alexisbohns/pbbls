package app.pbbls.android.features.path.capture

import androidx.annotation.StringRes
import app.pbbls.android.R

/**
 * Capture (record-flow D1): the photo, then the moment page that holds the name,
 * the when chip and a few words. The page is the last step and sits against the saves.
 */
enum class CaptureStep(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int? = null,
) {
    PHOTO(R.string.record_photo_title, R.string.record_photo_subtitle),
    MOMENT(R.string.capture_moment_title),
    ;

    val next: CaptureStep? get() = entries.getOrNull(ordinal + 1)
    val previous: CaptureStep? get() = if (ordinal == 0) null else entries[ordinal - 1]
}

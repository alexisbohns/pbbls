package app.pbbls.android.core.ui

import androidx.annotation.StringRes
import app.pbbls.android.R
import app.pbbls.android.core.data.DataError

/**
 * The one mapping from a `save_moment` or `complete_pebble` failure to copy,
 * shared with iOS slug for slug (R10) and used by Capture, Complete, Edit
 * moment and Edit pebble. P0002 arrives as [DataError.NotFound] (Task 1.5),
 * which is `moment_not_found` on these writers.
 */
@StringRes
fun momentWriteErrorRes(error: DataError): Int =
    when (error) {
        DataError.Quota -> R.string.moment_error_media_quota
        DataError.Unauthorized -> R.string.record_signed_out_error
        DataError.NotFound -> R.string.moment_error_not_found
        is DataError.Conflict ->
            when (error.code) {
                "moment_empty" -> R.string.moment_error_empty
                "moment_not_found" -> R.string.moment_error_not_found
                "pebble_needs_emotion" -> R.string.pebble_error_needs_emotion
                "pebble_needs_valence" -> R.string.pebble_error_needs_valence
                "pebble_needs_domain" -> R.string.pebble_error_needs_domain
                "soul_not_owned" -> R.string.pebble_error_soul_gone
                "collection_not_owned" -> R.string.pebble_error_collection_gone
                else -> R.string.moment_save_error_generic
            }
        DataError.Network, is DataError.Unknown -> R.string.moment_save_error_generic
    }

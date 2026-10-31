package app.pbbls.android.features.home.checklist

import androidx.annotation.StringRes
import app.pbbls.android.R

/** A task row's title (iOS `home.task.<t>.title`). */
@get:StringRes
internal val ChecklistTask.titleRes: Int
    get() =
        when (this) {
            ChecklistTask.MOMENT -> R.string.home_task_moment_title
            ChecklistTask.PEBBLE -> R.string.home_task_pebble_title
            ChecklistTask.COLLECTION -> R.string.home_task_collection_title
            ChecklistTask.SOUL -> R.string.home_task_soul_title
            ChecklistTask.GLYPH -> R.string.home_task_glyph_title
        }

/** A task row's second line (iOS `home.task.<t>.line`). */
@get:StringRes
internal val ChecklistTask.lineRes: Int
    get() =
        when (this) {
            ChecklistTask.MOMENT -> R.string.home_task_moment_line
            ChecklistTask.PEBBLE -> R.string.home_task_pebble_line
            ChecklistTask.COLLECTION -> R.string.home_task_collection_line
            ChecklistTask.SOUL -> R.string.home_task_soul_line
            ChecklistTask.GLYPH -> R.string.home_task_glyph_line
        }

/** A task sheet's two sentences (iOS `home.task.<t>.body`). */
@get:StringRes
internal val ChecklistTask.bodyRes: Int
    get() =
        when (this) {
            ChecklistTask.MOMENT -> R.string.home_task_moment_body
            ChecklistTask.PEBBLE -> R.string.home_task_pebble_body
            ChecklistTask.COLLECTION -> R.string.home_task_collection_body
            ChecklistTask.SOUL -> R.string.home_task_soul_body
            ChecklistTask.GLYPH -> R.string.home_task_glyph_body
        }

/** A task sheet's one call to action (iOS `home.task.<t>.cta`). */
@get:StringRes
internal val ChecklistTask.ctaRes: Int
    get() =
        when (this) {
            ChecklistTask.MOMENT -> R.string.home_task_moment_cta
            ChecklistTask.PEBBLE -> R.string.home_task_pebble_cta
            ChecklistTask.COLLECTION -> R.string.home_task_collection_cta
            ChecklistTask.SOUL -> R.string.home_task_soul_cta
            ChecklistTask.GLYPH -> R.string.home_task_glyph_cta
        }

/** The illustration's one short TalkBack label (iOS `home.task.<t>.illustration`). */
@get:StringRes
internal val ChecklistTask.illustrationRes: Int
    get() =
        when (this) {
            ChecklistTask.MOMENT -> R.string.home_task_moment_illustration
            ChecklistTask.PEBBLE -> R.string.home_task_pebble_illustration
            ChecklistTask.COLLECTION -> R.string.home_task_collection_illustration
            ChecklistTask.SOUL -> R.string.home_task_soul_illustration
            ChecklistTask.GLYPH -> R.string.home_task_glyph_illustration
        }

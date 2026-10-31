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

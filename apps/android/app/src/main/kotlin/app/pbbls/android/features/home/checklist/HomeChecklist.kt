package app.pbbls.android.features.home.checklist

import app.pbbls.android.core.data.AchievementRecord

/** One of the five firsts, in the order Home lists them (iOS `HomeChecklist.Task`). */
enum class ChecklistTask(
    val slug: String,
) {
    MOMENT("first-moment"),
    PEBBLE("first-pebble"),
    COLLECTION("first-collection"),
    SOUL("first-soul"),
    GLYPH("first-glyph"),
}

/** Where the checklist stands (iOS `HomeChecklist.State`). */
enum class ChecklistState { UNAVAILABLE, IN_PROGRESS, CLAIMABLE, DONE }

/** One task's row: done once its achievement is unlocked. */
data class ChecklistRow(
    val task: ChecklistTask,
    val isDone: Boolean,
)

/**
 * Your first five (#1383 D11; iOS `HomeChecklist`): five one-shot achievements in a fixed order,
 * keyed by slug, and the claimed blue pebble that ends them. A pure projection of the catalog and
 * the unlocks: no client counts anything.
 *
 * iOS's rows also carry each unlock's date; nothing draws it, and Android's unlocks read is ids
 * only, so the rows here do not.
 */
data class HomeChecklist(
    val rows: List<ChecklistRow>,
    val state: ChecklistState,
) {
    val doneCount: Int get() = rows.count { it.isDone }

    /** Home draws the box only while there is something to do or to claim. */
    val isShown: Boolean get() = state == ChecklistState.IN_PROGRESS || state == ChecklistState.CLAIMABLE

    companion object {
        const val BLUE_SLUG = "blue-pebble"

        /**
         * [unlocked] are slugs. Unavailable when one of the six rows is missing from [catalog], or
         * retired and never unlocked (an unlock is permanent, so a retired row the user holds still
         * counts); done once the blue pebble is unlocked; claimable once the five are.
         */
        fun make(
            catalog: List<AchievementRecord>,
            unlocked: Set<String>,
        ): HomeChecklist {
            val rows = ChecklistTask.entries.map { ChecklistRow(it, it.slug in unlocked) }
            val bySlug = catalog.groupBy { it.slug }.mapValues { it.value.first() }
            val available =
                (ChecklistTask.entries.map { it.slug } + BLUE_SLUG).all { slug ->
                    val record = bySlug[slug] ?: return@all false
                    record.isActive || slug in unlocked
                }
            val state =
                when {
                    !available -> ChecklistState.UNAVAILABLE
                    BLUE_SLUG in unlocked -> ChecklistState.DONE
                    rows.all { it.isDone } -> ChecklistState.CLAIMABLE
                    else -> ChecklistState.IN_PROGRESS
                }
            return HomeChecklist(rows, state)
        }

        /** [make] from the unlocks read as achievement ids (`achievement_unlocks`), mapped to slugs by [catalog]. */
        fun fromUnlockIds(
            catalog: List<AchievementRecord>,
            unlockedIds: Set<String>,
        ): HomeChecklist {
            val slugById = catalog.associate { it.id to it.slug }
            return make(catalog, unlockedIds.mapNotNull { slugById[it] }.toSet())
        }
    }
}

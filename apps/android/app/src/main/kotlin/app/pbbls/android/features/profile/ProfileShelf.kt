package app.pbbls.android.features.profile

import app.pbbls.android.core.data.AchievementRecord
import app.pbbls.android.core.model.achievements.AchievementsPage

/**
 * The Profile shelf from the served row (#1307, D18, iOS `ProfileShelf`): the server picks and
 * orders the badges (the most recent outside the private families) and counts them; the catalog,
 * by slug, gives each its title and icon. A slug the catalog does not hold (a stale or failed
 * catalog read) still shows, from the served row's family and threshold.
 */
internal object ProfileShelf {
    fun badges(
        page: AchievementsPage?,
        catalog: Map<String, AchievementRecord>,
    ): List<AchievementRecord> =
        page?.shelf.orEmpty().map { item ->
            item.slug?.let(catalog::get)
                ?: AchievementRecord(
                    id = item.slug.orEmpty(),
                    slug = item.slug.orEmpty(),
                    family = item.family.orEmpty(),
                    threshold = item.threshold,
                    sortOrder = 0,
                    karmaReward = 0,
                    isActive = true,
                )
        }

    /** The served count, or null when the server sent none or none is earned: the shelf then just invites. */
    fun count(page: AchievementsPage?): Int? = page?.shelfCount?.takeIf { it > 0 }
}

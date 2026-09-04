package app.pbbls.android.features.karma

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.pbbls.android.services.AchievementRecord

/**
 * One card of an unlock moment: a badge that just unlocked, with the karma it
 * actually paid (as reported by `check_achievements()`, never the catalog's
 * current value).
 *
 * [record] carries the catalog row so the card can resolve its localized copy
 * in the composition (`achievementTitle` is `@Composable`); it is null only
 * when a check raced a catalog change, in which case [slug] headlines the card.
 */
data class AchievementMomentCard(
    val slug: String,
    val record: AchievementRecord?,
    val karmaGranted: Int,
)

/**
 * Explicit-fire entry point for the achievement unlock moment (D13).
 *
 * Sibling of [KarmaNotificationService], but a different shape of celebration:
 * where karma flashes a pastille that times out, an unlock opens a chained card
 * per badge that the user taps through, so there is no auto-dismiss here.
 *
 * Only the mutation path celebrates: the screen-open call in `AchievementsScreen`
 * is the retroactive grant and can return a veteran's whole history at once, so
 * it renders in the grid instead of chaining twenty cards.
 */
class AchievementNotificationService {
    /** The queue being celebrated (empty = nothing showing). */
    var cards: List<AchievementMomentCard> by mutableStateOf(emptyList())
        private set

    /** Index of the card on screen. */
    var index: Int by mutableIntStateOf(0)
        private set

    /** The card on screen, or null when the moment is idle. */
    val currentCard: AchievementMomentCard?
        get() = cards.getOrNull(index)

    val isShowingLastCard: Boolean
        get() = index + 1 >= cards.size

    fun present(cards: List<AchievementMomentCard>) {
        if (cards.isEmpty()) return
        this.cards = cards
        this.index = 0
    }

    /** Advances to the next card, ending the moment after the last one. */
    fun advance() {
        if (isShowingLastCard) dismiss() else index += 1
    }

    /**
     * Ends the moment immediately — tapping the scrim or pressing back skips
     * the rest of the queue. Dismissal is never blocking.
     */
    fun dismiss() {
        cards = emptyList()
        index = 0
    }
}

val LocalAchievementNotificationService =
    staticCompositionLocalOf<AchievementNotificationService> {
        error("LocalAchievementNotificationService not provided — wrap the tree in MainActivity's CompositionLocalProvider")
    }

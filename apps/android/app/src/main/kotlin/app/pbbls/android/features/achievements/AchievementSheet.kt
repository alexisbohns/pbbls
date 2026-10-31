package app.pbbls.android.features.achievements

import app.pbbls.android.core.model.achievements.AchievementCard

/** What a sheet shows (#1307). Explorer pushes Domains or Emotions on top; the year pushes another year. */
sealed interface AchievementSheet {
    /** The card `mark_achievements_seen` records when this sheet opens. */
    val card: AchievementCard

    data object Explorer : AchievementSheet {
        override val card = AchievementCard.EXPLORER
    }

    data object Emotions : AchievementSheet {
        override val card = AchievementCard.EMOTIONS
    }

    data object Domains : AchievementSheet {
        override val card = AchievementCard.DOMAINS
    }

    data object Pebbles : AchievementSheet {
        override val card = AchievementCard.PEBBLES
    }

    data class Year(
        val year: Int,
    ) : AchievementSheet {
        override val card: AchievementCard get() = AchievementCard.YEAR
    }
}

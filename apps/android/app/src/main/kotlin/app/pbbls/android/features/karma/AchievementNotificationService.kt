package app.pbbls.android.features.karma

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.pbbls.android.services.AchievementRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One achievement unlock moment. Several unlocks in a single
 * `check_achievements()` response collapse into one content value (web
 * parity): [single] carries the catalog row when exactly one badge unlocked —
 * the capsule resolves its localized title — else a count phrase shows.
 * [karmaTotal] is the sum the server actually emitted.
 */
data class AchievementUnlockedContent(
    val count: Int,
    val single: AchievementRecord?,
    val karmaTotal: Int,
)

/**
 * Explicit-fire entry point for the achievement unlock capsule. Sibling of
 * [KarmaNotificationService] — same presentation contract, its own slot so an
 * achievement moment and a karma flash fired by the same mutation coexist
 * instead of one replacing the other (the overlay stacks them; see
 * `KarmaOverlayHost`). Delight only — never authoritative over what is
 * unlocked.
 */
class AchievementNotificationService(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    /** Content shown in the pastille (null = hidden). Observed by the overlay. */
    var activeCapsule: AchievementUnlockedContent? by mutableStateOf(null)
        private set

    private var dismissJob: Job? = null

    fun notifyUnlocked(
        count: Int,
        single: AchievementRecord?,
        karmaTotal: Int,
    ) {
        if (count <= 0) return
        activeCapsule = AchievementUnlockedContent(count, single, karmaTotal)
        dismissJob?.cancel()
        dismissJob =
            scope.launch {
                delay(CAPSULE_DURATION_MS)
                activeCapsule = null
            }
    }

    fun dismiss() {
        dismissJob?.cancel()
        activeCapsule = null
    }

    companion object {
        /** Slightly longer than the karma flash: a badge title takes a beat more to read. */
        const val CAPSULE_DURATION_MS = 3_200L
    }
}

val LocalAchievementNotificationService =
    staticCompositionLocalOf<AchievementNotificationService> {
        error("LocalAchievementNotificationService not provided — wrap the tree in MainActivity's CompositionLocalProvider")
    }

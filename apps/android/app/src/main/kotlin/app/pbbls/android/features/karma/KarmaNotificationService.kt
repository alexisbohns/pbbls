package app.pbbls.android.features.karma

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One karma-earned event to celebrate. Mirrors iOS KarmaEarnedContent. */
data class KarmaEarnedContent(
    val amount: Int,
    val reason: KarmaReason,
)

/**
 * Feature-agnostic "+N karma" flash entry point (D9/D10) — the
 * `KarmaNotificationService.swift` analog. Delight only, never authoritative
 * over the balance. Presentation is the bottom-center [KarmaEarnedCapsule];
 * the haptic fires in the composable (needs a View). `scope` is injectable so
 * the auto-dismiss is unit-testable off-device.
 */
class KarmaNotificationService(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    /** Content shown in the pastille (null = hidden). Observed by [KarmaOverlayHost]. */
    var activeCapsule: KarmaEarnedContent? by mutableStateOf(null)
        private set

    private var dismissJob: Job? = null

    /** Only positive credits celebrate; clawbacks/deletes stay silent (D10). */
    fun notifyEarned(
        amount: Int,
        reason: KarmaReason,
    ) {
        if (amount <= 0) return
        activeCapsule = KarmaEarnedContent(amount, reason)
        dismissJob?.cancel()
        dismissJob =
            scope.launch {
                delay(CAPSULE_DURATION_MS)
                activeCapsule = null
            }
    }

    /** Tap-to-dismiss. */
    fun dismiss() {
        dismissJob?.cancel()
        activeCapsule = null
    }

    companion object {
        const val CAPSULE_DURATION_MS = 2_500L
    }
}

val LocalKarmaNotificationService =
    staticCompositionLocalOf<KarmaNotificationService> {
        error("LocalKarmaNotificationService not provided — wrap the tree in MainActivity's CompositionLocalProvider")
    }

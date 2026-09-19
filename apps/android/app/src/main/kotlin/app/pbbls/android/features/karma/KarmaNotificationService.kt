package app.pbbls.android.features.karma

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.pbbls.android.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

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
@Singleton
class KarmaNotificationService
    @Inject
    constructor(
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        /** Content shown in the pastille (null = hidden). Observed by [KarmaOverlayHost]. */
        var activeCapsule: KarmaEarnedContent? by mutableStateOf(null)
            private set

        private var dismissJob: Job? = null

        /**
         * Only positive credits celebrate; clawbacks/deletes stay silent (D10).
         *
         * [presentsCapsule] exists for the record flow's success screen (M58 D10),
         * which already shows the amount — a capsule over it would be redundant.
         * The sound and haptic are not suppressed: they are the celebration, and
         * they ride on the capsule's composable, so a caller passing `false` gets a
         * silent credit by design.
         */
        fun notifyEarned(
            amount: Int,
            reason: KarmaReason,
            presentsCapsule: Boolean = true,
        ) {
            if (amount <= 0 || !presentsCapsule) return
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

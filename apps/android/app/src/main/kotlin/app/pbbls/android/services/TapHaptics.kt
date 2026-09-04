package app.pbbls.android.services

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * The four tap flavors the record flow uses — mirrors iOS `TapHaptic`.
 *
 * A closed set rather than a raw [HapticFeedbackConstants] int at each call
 * site: the flow's rule is "a haptic on every tap", and naming the *reason*
 * (rather than the constant) is what lets a test assert the mapping and what
 * keeps a new step from inventing a fifth texture.
 */
enum class TapHaptic {
    /** Picking a tile, toggling a soul — the most common tap in the flow. */
    SELECTION,

    /** Step changed, forward or back. */
    ADVANCE,

    /** The pebble published. */
    SUCCESS,

    /** A blocked advance or a failed publish. */
    WARNING,
}

/**
 * Thin wrapper over the platform view-level feedback constants, used for every
 * tap in the record flow.
 *
 * Deliberately not the Compose `LocalHapticFeedback` API: `HapticFeedbackType`
 * exposes a narrower set than the platform constants, and the flow wants four
 * distinct textures. `View.performHapticFeedback` is also what
 * `KarmaEarnedCapsule` and `AchievementMomentOverlay` already use, so the app
 * has one mechanism rather than two.
 *
 * Every constant here is API 30 or lower and minSdk is 33, so there is no
 * version guard (see the API-33-only rule in `apps/android/CLAUDE.md`).
 */
object TapHaptics {
    /**
     * Plays [haptic] on [view]. Pure dispatch — the flow model owns *when*, this
     * owns *what*.
     */
    fun play(
        view: View,
        haptic: TapHaptic,
    ) {
        view.performHapticFeedback(constantFor(haptic))
    }

    /**
     * Constant per flavor. `internal` and separate from [play] so a JVM unit
     * test can assert the mapping without a `View`.
     */
    internal fun constantFor(haptic: TapHaptic): Int =
        when (haptic) {
            // A light tick, the texture Android uses for a picker detent.
            TapHaptic.SELECTION -> HapticFeedbackConstants.CLOCK_TICK
            TapHaptic.ADVANCE -> HapticFeedbackConstants.CONTEXT_CLICK
            TapHaptic.SUCCESS -> HapticFeedbackConstants.CONFIRM
            TapHaptic.WARNING -> HapticFeedbackConstants.REJECT
        }
}

/**
 * A `(TapHaptic) -> Unit` bound to the current view, for injecting into
 * `RecordFlowModel`.
 *
 * Remembered on the view so the model — constructed once per flow — keeps a
 * stable closure rather than one that captures a stale composition.
 */
@Composable
fun rememberTapHaptics(): (TapHaptic) -> Unit {
    val view = LocalView.current
    return remember(view) { { haptic: TapHaptic -> TapHaptics.play(view, haptic) } }
}

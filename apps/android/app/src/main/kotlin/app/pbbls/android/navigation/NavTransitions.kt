package app.pbbls.android.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.navigation3.ui.NavDisplay

/**
 * The motions this app navigates with (#852, #940).
 *
 * Modal keys slide up from the bottom — the `fullScreenCover` analog the covers
 * used to fake by appearing instantly. Browse pushes use an M3 shared-axis
 * slide, so a push reads as lateral movement within a tab. A detail that opens
 * *beside* its list rather than over it uses [split], a fade-through.
 *
 * These are handed to `NavDisplay` as per-entry metadata rather than decided in
 * a `when` at the display, so a key's animation travels with the key.
 *
 * Every lambda parameter below is named rather than left implicit. The three
 * spec builders are extension lambdas on `AnimatedContentTransitionScope`, and
 * `predictivePopTransitionSpec` carries an extra `Int` parameter — so a bare
 * `it` inside one of them is ambiguous between that parameter and the slide
 * offset lambda's own. Naming both is what keeps this compiling.
 */
object NavTransitions {
    private const val DURATION_MS = 350
    private const val FADE_MS = 200

    /** Slide-up / slide-down. Applied to every non-[BarKey]. */
    val modal: Map<String, Any> =
        NavDisplay.transitionSpec {
            slideInVertically(tween(DURATION_MS)) { height -> height } togetherWith fadeOut(tween(FADE_MS))
        } +
            NavDisplay.popTransitionSpec {
                fadeIn(tween(FADE_MS)) togetherWith slideOutVertically(tween(DURATION_MS)) { height -> height }
            } +
            // The Int parameter is the swipe edge; this app animates the same
            // way from either edge, so it is deliberately ignored.
            NavDisplay.predictivePopTransitionSpec { _ ->
                fadeIn(tween(FADE_MS)) togetherWith slideOutVertically(tween(DURATION_MS)) { height -> height }
            }

    /** M3 shared-axis X. Applied to [BarKey] pushes, except the [split] ones. */
    val push: Map<String, Any> =
        NavDisplay.transitionSpec { sharedAxisForward() } +
            NavDisplay.popTransitionSpec { sharedAxisBackward() } +
            NavDisplay.predictivePopTransitionSpec { _ -> sharedAxisBackward() }

    /**
     * M3 fade-through, for a pebble opening beside Path (#940). Idle Path is a
     * full-width scene and Path with a pebble is a two-pane one, so opening a
     * pebble is a scene change: a shared-axis slide would push the whole
     * full-width Path out while the narrowed one slides back in. Fading keeps
     * Path where it is. On a phone the entry is a sheet, an overlay these
     * specs never reach.
     */
    val split: Map<String, Any> =
        NavDisplay.transitionSpec { fadeThrough() } +
            NavDisplay.popTransitionSpec { fadeThrough() } +
            NavDisplay.predictivePopTransitionSpec { _ -> fadeThrough() }

    // The incoming scene takes the push's duration, the outgoing one the
    // quicker fade every spec here leaves with.
    private fun fadeThrough() = fadeIn(tween(DURATION_MS)) togetherWith fadeOut(tween(FADE_MS))

    private fun AnimatedContentTransitionScope<*>.sharedAxisForward() =
        (
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(DURATION_MS)) +
                fadeIn(tween(FADE_MS))
        ) togetherWith (
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(DURATION_MS)) +
                fadeOut(tween(FADE_MS))
        )

    private fun AnimatedContentTransitionScope<*>.sharedAxisBackward() =
        (
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(DURATION_MS)) +
                fadeIn(tween(FADE_MS))
        ) togetherWith (
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(DURATION_MS)) +
                fadeOut(tween(FADE_MS))
        )

    /**
     * The metadata a key should carry, chosen by whether it shows the bar. A
     * [split] key does not use this: its entry asks for [split] by name.
     */
    fun forKey(key: PebblesKey): Map<String, Any> = if (key is BarKey) push else modal
}

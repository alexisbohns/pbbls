package app.pbbls.android.features.shared.ripples

import androidx.compose.ui.graphics.Color
import app.pbbls.android.theme.PebblesColors

/**
 * Logical color slot for a Ripple stroke — ports iOS `RippleStrokeColor.swift`.
 * [color] resolves a tone against the active palette bundle (a pure function,
 * not a composable, so DrawScope lambdas can call it).
 */
enum class RippleStrokeTone {
    DEFAULT, // outside the user's current level
    ACTIVE, // within level, user created a pebble today
    INACTIVE, // within level, user has NOT created a pebble today
}

/**
 * Pure mapping from `(strokeId, level, activeToday)` to a [RippleStrokeTone].
 * Encodes the truth table from issue #442 verbatim:
 *   - strokeId > level                  → DEFAULT
 *   - strokeId <= level &&  activeToday → ACTIVE
 *   - strokeId <= level && !activeToday → INACTIVE
 */
fun rippleStrokeTone(
    strokeId: Int,
    level: Int,
    activeToday: Boolean,
): RippleStrokeTone =
    when {
        strokeId > level -> RippleStrokeTone.DEFAULT
        activeToday -> RippleStrokeTone.ACTIVE
        else -> RippleStrokeTone.INACTIVE
    }

/** Resolved theme-aware color for this tone. */
fun RippleStrokeTone.color(colors: PebblesColors): Color =
    when (this) {
        RippleStrokeTone.DEFAULT -> colors.system.muted
        RippleStrokeTone.ACTIVE -> colors.accent.primary
        RippleStrokeTone.INACTIVE -> colors.system.secondary
    }

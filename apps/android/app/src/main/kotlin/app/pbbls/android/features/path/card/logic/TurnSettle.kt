package app.pbbls.android.features.path.card.logic

import kotlin.math.abs

/**
 * The card's drag-to-turn (pebble card spec §Behaviour). Progress 0 is the face that was
 * up when the drag began, 1 the other. Dragging toward the start edge (negative x in LTR;
 * callers flip for RTL) turns it.
 */
internal object TurnSettle {
    /** Turns of the card per second past which a release carries in its direction. */
    const val FLICK = 1.5f

    /** The turn a drag of [dragPx] makes across a card [widthPx] wide, 0 to 1; 0 while the card has no width. */
    fun progress(
        dragPx: Float,
        widthPx: Float,
    ): Float = if (widthPx <= 0f) 0f else (-dragPx / widthPx).coerceIn(0f, 1f)

    /**
     * Where a release settles: 1 completes the turn, 0 reverts it. A flick carries in its
     * direction whatever the progress; a slower release goes to the nearer face.
     *
     * @param velocityPerSecond turns per second, positive toward the other face (callers convert).
     */
    fun target(
        progress: Float,
        velocityPerSecond: Float,
    ): Float =
        when {
            abs(velocityPerSecond) >= FLICK -> if (velocityPerSecond > 0f) 1f else 0f
            else -> if (progress >= 0.5f) 1f else 0f
        }
}

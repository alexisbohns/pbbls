package app.pbbls.android.core.designsystem

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

/**
 * Positions on a ring (#1207): the month's circle of day dots (31 slots, a 1.5-slot gap), the
 * garland (32 slots, the tie in the gap at the foot), the hours dial ([continuous], noon on top)
 * and the gem's seven positions turned by its rotation.
 *
 * The circle is cut into `slots + gapSlots` equal steps. Slot 0 sits half the gap past [startDeg]
 * so the gap is centred on [startDeg]; slot values may be fractional. Angles are in degrees,
 * 0 at three o'clock and growing clockwise on screen (y down), as `DrawScope` measures them.
 */
class RadialLayout(
    val slots: Int,
    val startDeg: Float = -90f,
    val gapSlots: Float = 0f,
    val clockwise: Boolean = true,
    private val origin: Float = 0f,
) {
    init {
        require(slots > 0) { "a ring needs at least one slot" }
    }

    val stepDeg: Float get() = 360f / (slots + gapSlots)

    fun angleDeg(slot: Float): Float {
        val turn = (gapSlots / 2 + slot - origin) * stepDeg
        return startDeg + if (clockwise) turn else -turn
    }

    fun angleDeg(slot: Int): Float = angleDeg(slot.toFloat())

    fun point(
        slot: Float,
        center: Offset,
        radius: Float,
    ): Offset {
        val rad = Math.toRadians(angleDeg(slot).toDouble())
        return Offset(center.x + radius * cos(rad).toFloat(), center.y + radius * sin(rad).toFloat())
    }

    fun point(
        slot: Int,
        center: Offset,
        radius: Float,
    ): Offset = point(slot.toFloat(), center, radius)

    companion object {
        /** A value on a closed scale ([period] units around), with [top] at twelve o'clock: the dial is `continuous(24, top = 12f)`. */
        fun continuous(
            period: Int,
            top: Float = 0f,
        ): RadialLayout = RadialLayout(slots = period, startDeg = -90f, origin = top)
    }
}

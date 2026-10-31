package app.pbbls.android.features.achievements.figures

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

/** The Explorer wheel's geometry (the board's `wheel`, `WR`), in board px of a 300 box centred on 0. */
object ExplorerWheelGeometry {
    const val VIEW = 300f
    const val FIRST_FIVE_R = 44f
    const val SIX_R = 68f
    const val TWENTY_FIVE_R = 90f
    const val DOMAINS_R = 112f
    const val EMOTIONS_R = 134f
    const val CENTRE_WIDTH = 25f
    const val DOMAIN_WIDTH = 9f
    const val TWENTY_FIVE_WIDTH = 6.2f
    const val GHOST_R = 3.2f
    const val SMALL_GHOST_R = 2.6f
    const val ARC_INSET = 1.2f
    const val TICK_IN = 6f
    const val TICK_OUT = 11f

    /** Degrees clockwise from twelve o'clock, y down. */
    fun polar(
        r: Float,
        deg: Float,
    ): Offset {
        val a = Math.toRadians(deg.toDouble())
        return Offset((r * sin(a)).toFloat(), (-r * cos(a)).toFloat())
    }

    /**
     * The first five, the [i]th as served, clockwise from the top: 72° apart for the board's five.
     * Placed by position in the server's list, so a task this client does not know keeps its place.
     */
    fun firstFiveAngle(
        i: Int,
        of: Int = 5,
    ): Float = i * 360f / of.coerceAtLeast(1)

    /** The six firsts, the [i]th as served, half a step off the top: 30°, 90° … 330° for the board's six. */
    fun sixAngle(
        i: Int,
        of: Int = 6,
    ): Float = (i + 0.5f) * 360f / of.coerceAtLeast(1)

    fun twentyFiveAngle(i: Int): Float = (i + 0.5f) * 360f / TWENTY_FIVE_SEATS

    fun domainAngle(
        i: Int,
        of: Int,
    ): Float = (i + 0.5f) * 360f / of.coerceAtLeast(1)

    /** The rosace's seats, so the wheel's emotions sit where the rosace's facets do. */
    fun seatAngle(
        seat: Int,
        seats: Int,
    ): Float = (seat - 2.5f) * 360f / seats.coerceAtLeast(1)

    data class Arc(
        val family: String?,
        val startDeg: Float,
        val sweepDeg: Float,
        val tickDeg: Float,
    )

    fun familyArcs(
        runs: List<FamilyRun>,
        seats: Int,
    ): List<Arc> {
        val step = 360f / seats.coerceAtLeast(1)
        return runs.map { run ->
            val a0 = seatAngle(run.start, seats) - step / 2
            val a1 = seatAngle(run.start + run.size - 1, seats) + step / 2
            Arc(run.family, a0 + ARC_INSET, (a1 - ARC_INSET) - (a0 + ARC_INSET), a0)
        }
    }
}

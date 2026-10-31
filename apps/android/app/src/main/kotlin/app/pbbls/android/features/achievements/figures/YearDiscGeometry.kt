package app.pbbls.android.features.achievements.figures

import androidx.compose.ui.geometry.Offset
import app.pbbls.android.core.model.achievements.AchievementMonth
import app.pbbls.android.core.model.achievements.MonthState
import kotlin.math.cos
import kotlin.math.sin

/**
 * How one month draws on the docked disc. [counterR] is the place its level reached. [climb] is
 * the stretch of spoke inked, from and to a radius. [muted] holds for a month the server does not
 * call kept (no pebble, still to come, or a state this client does not know, drawn as quiet).
 */
data class MonthMark(
    val month: Int,
    val angle: Float,
    val counterR: Float,
    val climb: ClosedFloatingPointRange<Float>?,
    val climbInHue: Boolean,
    val goalNotch: Boolean,
    val rim: Boolean,
    val muted: Boolean,
    val upcoming: Boolean,
)

/** The year disc (g/112, docked; the board's `yearDiscDocked`), in its 220 view from 10 to 230, centred at 120. */
object YearDiscGeometry {
    const val VIEW_MIN = 10f
    const val VIEW = 220f
    const val CENTRE = 120f
    val RADII = floatArrayOf(30f, 50f, 80f)
    const val SPOKE_IN = 14f
    const val SPOKE_OUT = 88f
    const val NOTCH_HALF = 3f
    const val NUMERAL_R = 96f
    const val GOAL_NOTCH_DEG = 4.6f
    const val INNER_RING = 52f
    const val INNER_RING_2 = 48.5f
    const val COUNTER_WIDTH = 12.5f
    const val CLIMB_GAP = 6f
    const val RIM_CLIMB_STOP = 7f
    private val NUMERALS = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")

    /** The stone's edge: the board's 40-point outline, verbatim. */
    const val STONE =
        "M225.1,120.0 L223.0,136.3 L217.4,151.6 L211.9,166.8 L205.0,181.7 L192.4,192.4 L181.0,203.9 L167.6,213.5 " +
            "L151.9,218.2 L136.1,221.4 L120.0,225.2 L103.6,223.5 L87.6,219.6 L72.9,212.5 L58.7,204.4 L46.3,193.7 " +
            "L34.7,182.0 L27.8,167.0 L20.4,152.4 L17.4,136.3 L15.6,120.0 L16.7,103.6 L20.4,87.6 L27.0,72.6 L36.8,59.6 " +
            "L45.4,45.4 L58.2,35.0 L73.4,28.6 L88.0,21.5 L103.9,18.3 L120.0,14.9 L136.0,18.8 L152.0,21.7 L167.3,27.1 " +
            "L180.7,36.5 L192.8,47.2 L203.3,59.5 L213.5,72.4 L220.4,87.4 L222.5,103.8 Z"

    /** The stone's grain: x, y, r, verbatim. */
    val GRAIN: List<Triple<Float, Float, Float>> =
        listOf(
            Triple(217.6f, 135.5f, 0.7f),
            Triple(25.8f, 134.9f, 0.8f),
            Triple(105.0f, 25.2f, 0.6f),
            Triple(37.7f, 179.8f, 0.9f),
            Triple(25.9f, 134.9f, 0.8f),
            Triple(189.4f, 50.6f, 0.8f),
            Triple(209.8f, 74.2f, 0.7f),
            Triple(89.4f, 25.9f, 0.9f),
            Triple(52.0f, 52.0f, 0.8f),
            Triple(25.7f, 105.1f, 0.8f),
            Triple(217.5f, 135.4f, 0.8f),
            Triple(197.8f, 63.5f, 0.8f),
            Triple(75.7f, 207.0f, 0.9f),
            Triple(150.1f, 27.3f, 0.7f),
            Triple(25.1f, 135.0f, 0.6f),
            Triple(135.0f, 214.5f, 0.5f),
            Triple(89.0f, 24.5f, 0.9f),
            Triple(75.3f, 32.2f, 0.5f),
            Triple(25.7f, 134.9f, 0.6f),
            Triple(63.8f, 42.6f, 0.7f),
            Triple(176.8f, 41.8f, 0.5f),
            Triple(120.0f, 18.6f, 0.8f),
            Triple(105.1f, 26.1f, 0.6f),
            Triple(197.9f, 176.6f, 0.5f),
            Triple(120.0f, 23.9f, 0.9f),
            Triple(164.2f, 206.7f, 0.5f),
            Triple(42.2f, 176.5f, 0.9f),
            Triple(178.3f, 200.2f, 0.7f),
            Triple(89.6f, 213.6f, 0.7f),
            Triple(204.2f, 162.9f, 0.5f),
            Triple(207.2f, 75.6f, 0.8f),
            Triple(176.1f, 42.8f, 0.8f),
            Triple(120.0f, 219.1f, 0.5f),
            Triple(219.4f, 104.3f, 0.7f),
            Triple(90.6f, 29.7f, 0.6f),
            Triple(90.5f, 210.8f, 0.9f),
            Triple(216.3f, 88.7f, 0.6f),
            Triple(120.0f, 220.0f, 0.5f),
            Triple(135.4f, 22.6f, 0.7f),
            Triple(20.7f, 135.7f, 0.5f),
        )

    /** January at twelve o'clock, clockwise, 30° a month. */
    fun angle(month: Int): Float = (month - 1) * 30f

    fun point(
        r: Float,
        deg: Float,
    ): Offset {
        val a = Math.toRadians(deg.toDouble())
        return Offset(CENTRE + (r * sin(a)).toFloat(), CENTRE - (r * cos(a)).toFloat())
    }

    /** A notch across a month's spoke at radius [r]: 3 either side, perpendicular. */
    fun notch(
        month: Int,
        r: Float,
    ): Pair<Offset, Offset> {
        val a = Math.toRadians(angle(month).toDouble())
        val p = point(r, angle(month))
        val across = Offset(cos(a).toFloat(), sin(a).toFloat()) * NOTCH_HALF
        return (p - across) to (p + across)
    }

    fun numeral(month: Int): String = NUMERALS[month - 1]

    /** The server's level, progress and state, placed: nothing here compares a count with a target. */
    fun mark(m: AchievementMonth): MonthMark {
        val level = m.level.coerceIn(0, 2)
        val progress = if (level >= 2) 0f else m.progress
        val upcoming = m.state == MonthState.UPCOMING
        val muted = m.state != MonthState.KEPT
        val climb =
            when {
                level == 2 -> (RADII[1] + CLIMB_GAP)..(RADII[2] - RIM_CLIMB_STOP)
                progress > 0f -> {
                    val from = RADII[level] + CLIMB_GAP
                    from..(from + progress * (RADII[level + 1] - RADII[level] - 2 * CLIMB_GAP))
                }
                else -> null
            }
        return MonthMark(
            month = m.month,
            angle = angle(m.month),
            counterR = RADII[level],
            climb = climb,
            climbInHue = level >= 1 && !muted,
            goalNotch = level == 0 && progress > 0f && !muted,
            rim = level == 2,
            muted = muted,
            upcoming = upcoming,
        )
    }

    /** The disc read aloud (spec §5): the server's months on the rim, on the inner circle, on their way. */
    fun onRim(months: List<AchievementMonth>): List<Int> = months.filter { it.level >= 2 }.map { it.month }

    fun onInner(months: List<AchievementMonth>): List<Int> = months.filter { it.level == 1 }.map { it.month }

    fun onTheirWay(months: List<AchievementMonth>): List<Int> =
        months.filter { it.level == 0 && it.progress > 0f && it.state != MonthState.UPCOMING }.map { it.month }
}

package app.pbbls.android.features.achievements.figures

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The rosace's cut (the board's `gem`), in dp of a [size] box; angles in radians, 0 at three o'clock. */
class RosaceGeometry(
    val size: Float,
    labels: Boolean,
    val seats: Int,
) {
    val centre = size / 2
    val radius = size * if (labels) 0.345f else 0.44f
    val inner = radius * 0.85f
    val core = radius * 0.47f
    private val step = 2 * PI / seats.coerceAtLeast(1)

    fun angle(i: Int): Double = -PI / 2 + (i - 2.5) * step

    fun point(
        r: Float,
        a: Double,
    ): Offset = Offset(centre + (cos(a) * r).toFloat(), centre + (sin(a) * r).toFloat())

    fun kite(k: Int): List<Offset> =
        listOf(point(inner, angle(k) - step / 2), point(radius, angle(k)), point(inner, angle(k) + step / 2), point(core, angle(k)))

    fun star(k: Int): List<Offset> = listOf(point(core, angle(k)), point(inner, angle(k) + step / 2), point(core, angle(k + 1)))

    fun silhouette(): List<Offset> = (0 until seats).flatMap { k -> listOf(point(inner, angle(k) - step / 2), point(radius, angle(k))) }

    fun freshDot(k: Int): Offset = point(radius + 6.5f, angle(k))

    /** A family boundary's tick, outside the rim. */
    fun tick(start: Int): Pair<Offset, Offset> = point(radius + 3f, angle(start) - step / 2) to point(radius + 9f, angle(start) - step / 2)

    /** Where a family's name sits: 22 past the rim, at its run's middle. */
    fun label(
        start: Int,
        size: Int,
    ): Offset = point(radius + 22f, angle(start) + (size - 1) / 2.0 * step)
}

package app.pbbls.android.features.path.crop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.common.SeededGenerator
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * A rectangle drawn by hand (#1659, iOS `WobblyRectangle`): each corner nudged and each edge bowed in
 * or out by up to [amount] of its length, the same way for the same seed, and the same way as on iOS.
 * Edges are curves, not lines, so it reads as a pebble, not a sketch. With a [cornerRadius] each corner
 * is eased off by a curve tangent to its two edges, so the wobble survives the rounding.
 */
class WobblyRectangle(
    private val seed: String,
    private val amount: Float = 0.03f,
    private val cornerRadius: Dp = 0.dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(path(size, with(density) { cornerRadius.toPx() }))

    internal fun path(
        size: Size,
        cornerRadiusPx: Float,
    ): Path {
        val rng = SeededGenerator(seed)
        val nudge = min(size.width, size.height) * amount * 0.5f
        val corners =
            listOf(Offset(0f, 0f), Offset(size.width, 0f), Offset(size.width, size.height), Offset(0f, size.height))
                .map { Offset(it.x + rng.unit().toFloat() * nudge, it.y + rng.unit().toFloat() * nudge) }

        // Never more than half the shorter side, so opposite corners never meet.
        val radius = max(0f, min(cornerRadiusPx, min(size.width, size.height) / 2))
        // Each edge runs between the points `radius` in from its two corners (the corners themselves without rounding).
        val starts = (0 until 4).map { inset(corners[it], corners[(it + 1) % 4], radius) }
        val ends = (0 until 4).map { inset(corners[(it + 1) % 4], corners[it], radius) }
        // Each edge's bowed control, drawn in edge order so the seed's sequence is unchanged. A rounded edge keeps
        // the bow's slope (not its depth), so the short run between two corners stays nearly flat instead of kinking.
        val controls: List<Offset?> =
            (0 until 4).map { i ->
                val from = corners[i]
                val to = corners[(i + 1) % 4]
                val length = hypot(to.x - from.x, to.y - from.y)
                if (length <= 0f) return@map null
                // Outward normal for a clockwise outline in y-down space.
                val normal = Offset((to.y - from.y) / length, -(to.x - from.x) / length)
                val bow = rng.unit().toFloat() * length * amount
                val run = hypot(ends[i].x - starts[i].x, ends[i].y - starts[i].y)
                val depth = bow * (run / length)
                val mid = lerp(starts[i], ends[i], 0.5f)
                // A quadratic's control at twice the bow puts the curve's midpoint at the bow.
                Offset(mid.x + normal.x * depth * 2, mid.y + normal.y * depth * 2)
            }

        val path = Path()
        if (radius == 0f) {
            path.moveTo(corners[0].x, corners[0].y)
            for (i in 0 until 4) {
                val to = corners[(i + 1) % 4]
                val control = controls[i]
                if (control != null) path.quadraticTo(control.x, control.y, to.x, to.y) else path.lineTo(to.x, to.y)
            }
            path.close()
            return path
        }
        // Each corner is a cubic whose handles continue the tangents of the two edges meeting there, so the join has no kink.
        val handle = radius * ARC_HANDLE
        path.moveTo(starts[0].x, starts[0].y)
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            val control = controls[i] ?: lerp(starts[i], ends[i], 0.5f)
            val nextControl = controls[j] ?: lerp(starts[j], ends[j], 0.5f)
            path.quadraticTo(control.x, control.y, ends[i].x, ends[i].y)
            val c1 = advance(ends[i], control, handle)
            val c2 = advance(starts[j], nextControl, handle)
            path.cubicTo(c1.x, c1.y, c2.x, c2.y, starts[j].x, starts[j].y)
        }
        path.close()
        return path
    }

    /** [point] moved [distance] further along the direction from [origin] to it. */
    private fun advance(
        point: Offset,
        origin: Offset,
        distance: Float,
    ): Offset {
        val length = hypot(point.x - origin.x, point.y - origin.y)
        if (length <= 0f) return point
        return Offset(point.x + (point.x - origin.x) / length * distance, point.y + (point.y - origin.y) / length * distance)
    }

    private fun inset(
        point: Offset,
        other: Offset,
        distance: Float,
    ): Offset {
        val length = hypot(other.x - point.x, other.y - point.y)
        if (distance <= 0f || length <= 0f) return point
        return lerp(point, other, min(distance / length, 0.5f))
    }

    private fun lerp(
        a: Offset,
        b: Offset,
        t: Float,
    ): Offset = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

    override fun equals(other: Any?): Boolean =
        other is WobblyRectangle && other.seed == seed && other.amount == amount && other.cornerRadius == cornerRadius

    override fun hashCode(): Int = (seed.hashCode() * 31 + amount.hashCode()) * 31 + cornerRadius.hashCode()

    private companion object {
        /** A cubic's control distance that approximates a circular quarter arc. */
        const val ARC_HANDLE = 0.5523f
    }
}

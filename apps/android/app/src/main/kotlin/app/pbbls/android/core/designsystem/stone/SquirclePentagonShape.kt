package app.pbbls.android.core.designsystem.stone

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The profile's squircle pentagon (spec D5), stretched to whatever size it is
 * drawn at; its design box is 128 × 122. Symmetric, so it needs no mirroring
 * right to left.
 */
data object SquirclePentagonShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(SquirclePentagon.path(size))
}

/**
 * The mock's `pentagon()` (`type/five/c3.html`, 2026-10-31 Type Bench): five
 * corners on a 64-unit radius around (64, 66) in a 128 × 122 box, the first at
 * the top; each corner a cubic over 28 % of the side with handles at 4 %, each
 * side a quadratic bowed 2 units outward at its middle.
 */
internal object SquirclePentagon {
    private const val WIDTH = 128.0
    private const val HEIGHT = 122.0
    private const val CX = 64.0
    private const val CY = 66.0
    private const val RADIUS = 64.0
    private const val CORNER = 0.28
    private const val HANDLE = 0.04
    private const val BOW = 1.0

    private sealed interface Segment {
        val end: Offset
    }

    private data class Cubic(
        val c1: Offset,
        val c2: Offset,
        override val end: Offset,
    ) : Segment

    private data class Quad(
        val control: Offset,
        override val end: Offset,
    ) : Segment

    /** The start point and the ten segments, in design units. */
    private val outline: Pair<Offset, List<Segment>> by lazy {
        val p =
            List(5) { i ->
                val a = -PI / 2 + i * 2 * PI / 5
                doubleArrayOf(CX + RADIUS * cos(a), CY + RADIUS * sin(a))
            }

        fun lerp(
            a: DoubleArray,
            b: DoubleArray,
            u: Double,
        ) = doubleArrayOf(a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u)

        fun DoubleArray.offset() = Offset(this[0].toFloat(), this[1].toFloat())
        var start = Offset.Zero
        val segments = ArrayList<Segment>(10)
        for (i in 0 until 5) {
            val prev = p[(i + 4) % 5]
            val c = p[i]
            val next = p[(i + 1) % 5]
            val a = lerp(c, prev, CORNER)
            val b = lerp(c, next, CORNER)
            if (i == 0) start = a.offset()
            segments += Cubic(lerp(c, prev, HANDLE).offset(), lerp(c, next, HANDLE).offset(), b.offset())
            val nextA = lerp(next, c, CORNER)
            val mid = lerp(b, nextA, 0.5)
            val outX = mid[0] - CX
            val outY = mid[1] - CY
            val l = hypot(outX, outY)
            val control = doubleArrayOf(mid[0] + outX / l * BOW * 2, mid[1] + outY / l * BOW * 2)
            segments += Quad(control.offset(), nextA.offset())
        }
        start to segments
    }

    private fun scaler(size: Size): (Offset) -> Offset {
        val sx = (size.width / WIDTH).toFloat()
        val sy = (size.height / HEIGHT).toFloat()
        return { Offset(it.x * sx, it.y * sy) }
    }

    fun path(size: Size): Path {
        val s = scaler(size)
        val (start, segments) = outline
        return Path().apply {
            s(start).let { moveTo(it.x, it.y) }
            for (segment in segments) {
                val end = s(segment.end)
                when (segment) {
                    is Cubic -> {
                        val c1 = s(segment.c1)
                        val c2 = s(segment.c2)
                        cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
                    }
                    is Quad -> {
                        val c = s(segment.control)
                        quadraticTo(c.x, c.y, end.x, end.y)
                    }
                }
            }
            close()
        }
    }

    /** The outline flattened to [perSegment] points a segment, for tests. */
    fun points(
        size: Size,
        perSegment: Int = 32,
    ): List<Offset> {
        val s = scaler(size)
        val (start, segments) = outline
        val out = ArrayList<Offset>(segments.size * perSegment)
        var from = start
        for (segment in segments) {
            for (j in 0 until perSegment) {
                val t = j.toFloat() / perSegment
                val u = 1 - t
                out +=
                    s(
                        when (segment) {
                            is Cubic ->
                                from * (u * u * u) + segment.c1 * (3 * u * u * t) + segment.c2 * (3 * u * t * t) +
                                    segment.end * (t * t * t)
                            is Quad -> from * (u * u) + segment.control * (2 * u * t) + segment.end * (t * t)
                        },
                    )
            }
            from = segment.end
        }
        return out
    }
}

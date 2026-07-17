package app.pbbls.android.features.glyph.carve

import java.util.Locale
import kotlin.math.floor
import kotlin.math.round

/**
 * Quadratic-midpoint SVG path serializer — ports iOS `SVGPath.svgPathString`
 * with **byte parity** (M43 design D1): the same stroke points must serialize
 * to the identical `d` string on every surface, because web/iOS/the server
 * re-render these strings verbatim.
 *
 * Shape: `M{p0}`, then for each interior point a `Q{p_i} {mid(p_i, p_i+1)}`,
 * then an explicit ` L{p_last}`. One point emits the zero-length
 * `M{p} L{p}` pair (a tap commits a dot); two points a plain `M L`; empty → "".
 *
 * Numbers: round half-away-from-zero to 2 decimals; whole values print with
 * NO decimal point (Kotlin's `Double.toString` would give "10.0" and break
 * parity); fractional values print at 4 significant digits with trailing
 * zeros stripped (the `%.4g` iOS uses — Java pads where C strips, so the
 * strip is explicit here). Carve coordinates live in 0..200, so `%g` never
 * reaches its scientific-notation regime.
 */
object SvgPathSerializer {
    fun svgPathString(points: List<CarvePoint>): String {
        if (points.isEmpty()) return ""
        val first = points.first()
        if (points.size == 1) {
            return "M${fmt(first.x)},${fmt(first.y)} L${fmt(first.x)},${fmt(first.y)}"
        }
        if (points.size == 2) {
            val last = points[1]
            return "M${fmt(first.x)},${fmt(first.y)} L${fmt(last.x)},${fmt(last.y)}"
        }
        val builder = StringBuilder("M${fmt(first.x)},${fmt(first.y)}")
        for (i in 1..points.size - 2) {
            val control = points[i]
            val next = points[i + 1]
            val midX = (control.x + next.x) / 2
            val midY = (control.y + next.y) / 2
            builder.append(" Q${fmt(control.x)},${fmt(control.y)} ${fmt(midX)},${fmt(midY)}")
        }
        val last = points.last()
        builder.append(" L${fmt(last.x)},${fmt(last.y)}")
        return builder.toString()
    }

    private fun fmt(value: Double): String {
        val rounded = round(value * 100) / 100
        if (rounded == floor(rounded)) {
            return rounded.toLong().toString()
        }
        val formatted = String.format(Locale.ROOT, "%.4g", rounded)
        return if ('.' in formatted) formatted.trimEnd('0').trimEnd('.') else formatted
    }
}

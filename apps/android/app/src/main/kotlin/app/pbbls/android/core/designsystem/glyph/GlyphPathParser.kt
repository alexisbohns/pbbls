package app.pbbls.android.core.designsystem.glyph

import androidx.compose.ui.graphics.vector.PathNode

/**
 * Reads the normalised path data of `pbbls_glyphs.json` (spec D7): absolute
 * `M L C Q Z`, numbers split by spaces, commas or a sign. A command's extra
 * coordinate groups repeat it, an `M`'s as `L` (SVG 1.1 §8.3.2). Any other
 * command is skipped: the generator never writes one
 * (`packages/icons/fixtures/path-contract.json` is the contract; iOS's
 * `GlyphPathParser.swift` reads it the same way).
 */
object GlyphPathParser {
    data class Point(
        val x: Double,
        val y: Double,
    )

    sealed interface Segment {
        data class Move(
            val p: Point,
        ) : Segment

        data class Line(
            val p: Point,
        ) : Segment

        data class Cubic(
            val c1: Point,
            val c2: Point,
            val p: Point,
        ) : Segment

        data class Quad(
            val c: Point,
            val p: Point,
        ) : Segment

        data object Close : Segment
    }

    fun segments(d: String): List<Segment> =
        buildList {
            for (token in tokens(d)) {
                val n = token.numbers
                val points = (0 until n.size - 1 step 2).map { Point(n[it], n[it + 1]) }
                when (token.command) {
                    'M' -> points.forEachIndexed { i, p -> add(if (i == 0) Segment.Move(p) else Segment.Line(p)) }
                    'L' -> points.forEach { add(Segment.Line(it)) }
                    'C' -> for (i in 0 until points.size - 2 step 3) add(Segment.Cubic(points[i], points[i + 1], points[i + 2]))
                    'Q' -> for (i in 0 until points.size - 1 step 2) add(Segment.Quad(points[i], points[i + 1]))
                    'Z' -> add(Segment.Close)
                    else -> Unit
                }
            }
        }

    /** The same segments as Compose path nodes. */
    fun pathNodes(d: String): List<PathNode> =
        segments(d).map { s ->
            when (s) {
                is Segment.Move -> PathNode.MoveTo(s.p.x.toFloat(), s.p.y.toFloat())
                is Segment.Line -> PathNode.LineTo(s.p.x.toFloat(), s.p.y.toFloat())
                is Segment.Cubic ->
                    PathNode.CurveTo(
                        s.c1.x.toFloat(),
                        s.c1.y.toFloat(),
                        s.c2.x.toFloat(),
                        s.c2.y.toFloat(),
                        s.p.x.toFloat(),
                        s.p.y.toFloat(),
                    )
                is Segment.Quad -> PathNode.QuadTo(s.c.x.toFloat(), s.c.y.toFloat(), s.p.x.toFloat(), s.p.y.toFloat())
                Segment.Close -> PathNode.Close
            }
        }

    private class Token(
        val command: Char,
    ) {
        val numbers = mutableListOf<Double>()
    }

    private const val COMMANDS = "MLCQZmlcqzAaHhVvSsTt"

    private fun tokens(d: String): List<Token> {
        val out = mutableListOf<Token>()
        val number = StringBuilder()

        fun flush() {
            if (number.isNotEmpty()) {
                number.toString().toDoubleOrNull()?.let { value -> out.lastOrNull()?.numbers?.add(value) }
                number.clear()
            }
        }
        for (ch in d) {
            when (ch) {
                in COMMANDS -> {
                    flush()
                    out.add(Token(ch))
                }
                '-' -> {
                    if (number.isNotEmpty() && !number.endsWith('e')) flush()
                    number.append(ch)
                }
                '.' -> {
                    if (number.contains('.') && !number.contains('e')) flush()
                    number.append(ch)
                }
                'e', in '0'..'9' -> number.append(ch)
                else -> flush()
            }
        }
        flush()
        return out
    }
}

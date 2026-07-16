package app.pbbls.android.features.shared.ripples

import androidx.compose.ui.graphics.Path

/**
 * The six Ripple ring strokes, hand-ported from the SVG `<path d="…">`
 * definitions in issue #442 (coordinates transcribed 1:1 from iOS
 * `RippleStrokes.swift`). Authored against a 44×44 viewBox; the badge scales
 * them uniformly at draw time so they render correctly at any size.
 *
 * Indexed 1–6 from innermost to outermost, matching the stroke ids the
 * tone truth table uses.
 */
internal object RippleStrokes {
    val byId: Map<Int, Path> =
        mapOf(
            1 to stroke1(),
            2 to stroke2(),
            3 to stroke3(),
            4 to stroke4(),
            5 to stroke5(),
            6 to stroke6(),
        )

    private fun stroke1(): Path =
        Path().apply {
            moveTo(25.4147f, 30.7822f)
            cubicTo(22.7365f, 31.9714f, 19.5086f, 31.8785f, 16.4741f, 29.5504f)
            cubicTo(6.9764f, 22.2636f, 18.3687f, 7.65428f, 27.8664f, 14.941f)
            cubicTo(32.662f, 18.6202f, 32.1318f, 24.1663f, 29.2088f, 27.818f)
        }

    private fun stroke2(): Path =
        Path().apply {
            moveTo(34.1755f, 13.5272f)
            cubicTo(25.9708f, 1.34962f, 4.58761f, 9.44313f, 7.58572f, 25.087f)
        }

    private fun stroke3(): Path =
        Path().apply {
            moveTo(36.6088f, 19.5146f)
            cubicTo(39.4844f, 34.5339f, 18.0179f, 42.6778f, 10f, 31.0941f)
        }

    private fun stroke4(): Path =
        Path().apply {
            moveTo(41.458f, 26.9565f)
            cubicTo(39.2185f, 38.9628f, 23.9232f, 45.4638f, 11.4043f, 40.1005f)
        }

    private fun stroke5(): Path =
        Path().apply {
            moveTo(7.37405f, 37.1175f)
            cubicTo(-1.10595f, 29.2114f, 0.748869f, 9.24398f, 10.831f, 4.78223f)
        }

    private fun stroke6(): Path =
        Path().apply {
            moveTo(15.4023f, 2.71506f)
            cubicTo(26.241f, -0.507724f, 41.9999f, 7.38652f, 41.9999f, 21.8993f)
        }
}

/** The 44-unit source viewBox side the strokes are authored against. */
internal const val RIPPLE_VIEWBOX_SIDE = 44f

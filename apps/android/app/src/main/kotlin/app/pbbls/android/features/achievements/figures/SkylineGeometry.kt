package app.pbbls.android.features.achievements.figures

import androidx.compose.ui.geometry.Rect
import app.pbbls.android.core.model.achievements.CountingSection
import kotlin.math.min

/**
 * How much of a skyline stands: [lit] holds one entry per drawn stage, solid when the server lists
 * its step; the stage at [rising] (the server's next step, never a lit one) fills by [partial].
 */
data class SkylineFill(
    val lit: List<Boolean>,
    val rising: Int?,
    val partial: Float,
) {
    val stages: Int get() = lit.size

    /** How many stages stand, wherever they are on the ladder. */
    val full: Int get() = lit.count { it }

    val done: Boolean get() = lit.all { it }
}

/**
 * The skyline a counting stack draws, read off its levels and its next (never its count): each
 * ladder step the server lists as a level stands on its own, whatever the steps below it (a
 * skipped rung stays empty), and the server's next step rises by its progress.
 */
fun CountingSection.skylineFill(): SkylineFill {
    val stages = min(ladder.size, SkylineGeometry.STAGES.size)
    val steps = ladder.take(stages)
    val reached = levels.map { it.threshold }
    val lit = steps.map { it in reached }
    val rise = next?.takeIf { n -> n.progress > 0f && steps.indexOf(n.threshold).let { it >= 0 && !lit[it] } }
    return SkylineFill(lit, rise?.let { steps.indexOf(it.threshold) }, rise?.progress ?: 0f)
}

/** The board's one skyline (g/92, its second section), in its own units: the ground at y = 148. */
object SkylineGeometry {
    const val GROUND = 148f
    const val START = 16f

    val STAGES: List<String> =
        listOf(
            "M23 148 C23 143.3 29 143.3 29 148 Z",
            "M32.5 148 C32.1 136.9 34.9 133.2 40.7 133.2 C46.1 133.2 49.9 138.8 49.5 148 Z",
            "M53 148 C52.6 133.5 56.8 128.7 63.3 128.7 C68.8 128.7 72.4 136 72 148 Z",
            "M75.5 148 L76.7 135.5 L83.1 125.2 L89.9 127.5 L95.5 138.4 L95.5 148 Z",
            "M99 148 C98.6 128.4 105 121.8 112.8 121.8 C118.8 121.8 122.4 131.8 122 148 Z",
            "M125.5 148 L125.9 123.5 C126.3 116.7 133.4 116.1 134 122.8 L134 148 Z",
            "M141 148 L141 120.7 L137.5 120.7 C137.2 114.5 145.9 113.9 151.5 113.9 C157.1 113.9 165.8 114.5 165.5 120.7 " +
                "L162 120.7 L162 148 L158 148 L158 120.7 L145 120.7 L145 148 Z",
            "M169 148 C180.5 147.2 182 110.5 194.9 110.5 C208.4 110.5 207.4 145.8 217 148 Z",
        )
    val STAGE_X: List<Pair<Float, Float>> =
        listOf(23f to 29f, 32.5f to 49.5f, 53f to 72f, 75.5f to 95.5f, 99f to 122f, 125.5f to 134f, 137.5f to 165.5f, 169f to 217f)
    val STAGE_TOP: List<Float> = listOf(144.5f, 133.2f, 128.7f, 125.2f, 121.8f, 117.4f, 113.9f, 110.5f)

    /** A view onto the skyline: the board's `viewBox` and where its contour ends. */
    data class Viewport(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
        val end: Float,
    )

    /** The card's and the sheet's skyline (`12 104 216 48`), and the glyph lines' (`18 118 110 32`). */
    val BIG = Viewport(12f, 104f, 216f, 48f, end = 224f)
    val MINI = Viewport(18f, 118f, 110f, 32f, end = 126f)

    fun contour(
        stages: Int,
        end: Float,
    ): String {
        val endText = if (end % 1f == 0f) end.toInt().toString() else end.toString()
        return "M16 148 " + STAGES.take(stages).joinToString(" ") { "L" + it.drop(1).removeSuffix(" Z") } + " L$endText 148"
    }

    fun partialClip(
        stage: Int,
        fraction: Float,
    ): Rect {
        val (x0, x1) = STAGE_X[stage]
        val rise = (GROUND - STAGE_TOP[stage]) * fraction
        return Rect(left = x0 - 2f, top = GROUND - rise, right = x1 + 2f, bottom = GROUND + 1f)
    }
}

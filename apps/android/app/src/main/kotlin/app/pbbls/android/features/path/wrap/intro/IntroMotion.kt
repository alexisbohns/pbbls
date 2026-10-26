package app.pbbls.android.features.path.wrap.intro

import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.RevealMotion

/** How long a key takes to land, and the stamp's curves (iOS `IntroMotion`); the eases are [RevealMotion]'s. */
object IntroMotion {
    /** A Compose part: fades in and rises [riseOffset]. */
    const val RISE = 0.28

    /** A mark in a figure: grows from 0.4, overshooting once. */
    const val POP = 0.22

    /** A gem facet: its colour over the blank paper. */
    const val LIGHT = 0.18

    val riseOffset = 6.dp

    /** How far below its place a rising part still sits at [t], 0…1 of its beat, as a fraction of [riseOffset]: 0 once landed. */
    fun riseLift(t: Double): Double = 1 - RevealMotion.easeOut(t)

    /** When, 0…1 of its beat, a stamp meets the paper: its tick's moment. */
    const val STAMP_IMPACT = 0.6

    /** A stamp's press past its size at impact. */
    private const val STAMP_SQUASH = 0.04

    /** A stamp's scale: from 1.25 down to its impact, a slight squash there, back to 1. */
    fun stampScale(t: Double): Double {
        val c = RevealMotion.clamp(t)
        if (c < STAMP_IMPACT) {
            val u = c / STAMP_IMPACT
            return 1.25 - (0.25 + STAMP_SQUASH) * u * u
        }
        return 1 - STAMP_SQUASH * (1 - RevealMotion.easeOut((c - STAMP_IMPACT) / (1 - STAMP_IMPACT)))
    }

    /** A stamp's opacity: there by its impact. */
    fun stampOpacity(t: Double): Double = RevealMotion.clamp(t / STAMP_IMPACT)
}

/** The slow-fast-slow spacing of a run of beats: beat i of n lands when the eased run reaches i / (n − 1). */
object IntroCurve {
    fun times(
        count: Int,
        duration: Double,
    ): List<Double> =
        when {
            count <= 0 -> emptyList()
            count == 1 -> listOf(0.0)
            else -> List(count) { duration * RevealMotion.easeInOutInverse(it / (count - 1.0)) }
        }
}

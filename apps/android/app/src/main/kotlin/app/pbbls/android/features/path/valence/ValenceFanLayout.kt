package app.pbbls.android.features.path.valence

import app.pbbls.android.features.path.models.Valence
import app.pbbls.android.features.path.models.ValencePolarity
import app.pbbls.android.features.path.models.ValenceSizeGroup
import app.pbbls.android.features.path.render.PebbleOutlineGeometry

/**
 * Geometry of the valence fan: where each of the nine stones sits, and how big
 * it is — ports iOS `ValenceFanLayout.swift` constant for constant.
 *
 * Everything is expressed in a fixed **reference canvas** ([REFERENCE_WIDTH] ×
 * [REFERENCE_HEIGHT]), whose units the view reads as dp. Two consequences
 * worth stating: the fan reads identically on every device, and the layout is
 * verified with plain arithmetic instead of a snapshot — `ValenceFanLayoutTest`
 * asserts the bounds and ordering invariants below.
 *
 * The arrangement is a fan rather than a polar sweep. Polarity picks the
 * horizontal half-spread (lowlight left, neutral centre, highlight right) and
 * size picks the height off the bottom edge, with the centre stone of each ring
 * lifted a little above its two siblings. Stones grow and their gaps widen as
 * the fan rises, so the eye reads "bigger event" going up.
 *
 * Pure Kotlin — no Compose types — so the whole surface unit-tests on the JVM,
 * same rule as `WobbleRenderer`.
 */
object ValenceFanLayout {
    /**
     * The canvas every constant below is expressed in. It is exactly the
     * content width of a 375dp-wide device (375 − 2 × `Spacing.lg`), which is
     * the narrowest iOS supports and where the constants were tuned.
     *
     * Unlike iOS the view *may* scale this down — a Compose `BoxWithConstraints`
     * reports a bounded width even inside a vertical scroll, where SwiftUI's
     * `GeometryReader` has no ideal height to offer. See `ValenceFan`.
     */
    const val REFERENCE_WIDTH = 341f
    const val REFERENCE_HEIGHT = 324f

    /**
     * Stone height in reference units. Width follows from
     * [PebbleOutlineGeometry.aspectRatio], so each stone keeps its real
     * silhouette proportions.
     */
    fun stoneHeight(size: ValenceSizeGroup): Float =
        when (size) {
            ValenceSizeGroup.SMALL -> 40f
            ValenceSizeGroup.MEDIUM -> 71f
            ValenceSizeGroup.LARGE -> 104f
        }

    /**
     * Stone width in reference units, derived from the silhouette's own aspect
     * ratio — small stones are wider than tall, large ones taller than wide.
     */
    fun stoneWidth(size: ValenceSizeGroup): Float = stoneHeight(size) * PebbleOutlineGeometry.aspectRatio(size)

    /** Distance from the canvas's horizontal centre to a side stone's centre. */
    private fun halfSpread(size: ValenceSizeGroup): Float =
        when (size) {
            ValenceSizeGroup.SMALL -> 62f
            ValenceSizeGroup.MEDIUM -> 90f
            ValenceSizeGroup.LARGE -> 111f
        }

    /**
     * Sideways nudge applied to a whole ring, so the three rings do not stack
     * into a column grid. Small enough to read as a hand-strewn arrangement
     * rather than as a wonky one; symmetric within a ring, so it never disturbs
     * the lowlight → highlight ordering.
     */
    private fun ringDrift(size: ValenceSizeGroup): Float =
        when (size) {
            ValenceSizeGroup.SMALL -> 9f
            ValenceSizeGroup.MEDIUM -> -7f
            ValenceSizeGroup.LARGE -> 5f
        }

    /** Height of a side stone's centre above the canvas's bottom edge. */
    private fun sideRise(size: ValenceSizeGroup): Float =
        when (size) {
            ValenceSizeGroup.SMALL -> 31f
            ValenceSizeGroup.MEDIUM -> 109f
            ValenceSizeGroup.LARGE -> 225f
        }

    /**
     * Extra rise given to the ring's centre (neutral) stone, which is what bows
     * each row into an arc instead of a straight line.
     */
    private fun centreLift(size: ValenceSizeGroup): Float =
        when (size) {
            ValenceSizeGroup.SMALL -> 12f
            ValenceSizeGroup.MEDIUM -> 24f
            ValenceSizeGroup.LARGE -> 36f
        }

    /** Centre of a stone in reference space, origin top-left. */
    fun centreX(valence: Valence): Float {
        val size = valence.sizeGroup
        val spread =
            when (valence.polarity) {
                ValencePolarity.LOWLIGHT -> -halfSpread(size)
                ValencePolarity.NEUTRAL -> 0f
                ValencePolarity.HIGHLIGHT -> halfSpread(size)
            }
        return REFERENCE_WIDTH / 2f + spread + ringDrift(size)
    }

    fun centreY(valence: Valence): Float {
        val size = valence.sizeGroup
        val lift = if (valence.polarity == ValencePolarity.NEUTRAL) centreLift(size) else 0f
        return REFERENCE_HEIGHT - (sideRise(size) + lift)
    }

    /** Left edge of a stone's bounding box in reference space. */
    fun left(valence: Valence): Float = centreX(valence) - stoneWidth(valence.sizeGroup) / 2f

    /** Top edge of a stone's bounding box in reference space. */
    fun top(valence: Valence): Float = centreY(valence) - stoneHeight(valence.sizeGroup) / 2f
}

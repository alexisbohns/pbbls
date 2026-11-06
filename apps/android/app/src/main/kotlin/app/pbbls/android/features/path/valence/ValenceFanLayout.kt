package app.pbbls.android.features.path.valence

import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.ui.render.PebbleOutlineGeometry

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

    /** How much a chosen stone grows; the row labels must clear that too. */
    const val SELECTED_SCALE = 1.14f

    /** A rectangle in reference space, origin top-left. */
    data class Box(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top

        fun intersects(other: Box): Boolean = left < other.right && other.left < right && top < other.bottom && other.top < bottom
    }

    /**
     * The box each row label (Month, Week, Day; D7) draws in, reference space, up the left margin: one
     * in the top gap above the large ring, one between the large and medium rings, one beside the small
     * ring. Wide enough for the longest translated word in small caps, with slack. Ports iOS `rowLabelBox`.
     */
    fun rowLabelBox(size: ValenceSizeGroup): Box {
        val centreY =
            when (size) {
                ValenceSizeGroup.LARGE -> 28f
                ValenceSizeGroup.MEDIUM -> 166f
                ValenceSizeGroup.SMALL -> 295f
            }
        return Box(left = 4f, top = centreY - 7f, right = 64f, bottom = centreY + 7f)
    }

    /** A stone's bounding box as it draws when chosen ([SELECTED_SCALE] about its centre). */
    fun selectedFrame(valence: Valence): Box {
        val halfWidth = stoneWidth(valence.sizeGroup) * SELECTED_SCALE / 2f
        val halfHeight = stoneHeight(valence.sizeGroup) * SELECTED_SCALE / 2f
        return Box(
            left = centreX(valence) - halfWidth,
            top = centreY(valence) - halfHeight,
            right = centreX(valence) + halfWidth,
            bottom = centreY(valence) + halfHeight,
        )
    }

    /** Left edge of a stone's bounding box in reference space. */
    fun left(valence: Valence): Float = centreX(valence) - stoneWidth(valence.sizeGroup) / 2f

    /** Top edge of a stone's bounding box in reference space. */
    fun top(valence: Valence): Float = centreY(valence) - stoneHeight(valence.sizeGroup) / 2f
}

package app.pbbls.android.core.model.crop

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** A point or a vector in the crop's own units. Plain doubles, so the geometry runs on the JVM. */
data class CropPoint(
    val x: Double,
    val y: Double,
) {
    /** This vector turned by [radians], clockwise in y-down space (CoreGraphics' and Canvas' convention). */
    fun rotated(radians: Double): CropPoint {
        val c = cos(radians)
        val s = sin(radians)
        return CropPoint(x * c - y * s, x * s + y * c)
    }

    companion object {
        val Zero = CropPoint(0.0, 0.0)
    }
}

/** A size in the crop's own units. */
data class CropSize(
    val width: Double,
    val height: Double,
)

/**
 * Where the crop window sits on the photo (#1659, iOS `CropGeometry`, spec "Geometry"). Pure value:
 * no view sizes, no Compose. Window units: the window is `ratio.value` wide and 1 tall, centred on the
 * origin, y down; positive turns are clockwise.
 *
 * @property imageSize the working image's pixel size, upright (EXIF orientation applied).
 * @property quarterTurns clockwise quarter turns, 0…3 once normalised.
 * @property angle fine turn in degrees, −45…45 once normalised.
 * @property scale window units per image pixel.
 * @property focus the image point under the window centre, in pixels from the image centre, y down.
 */
data class CropGeometry(
    val imageSize: CropSize,
    val ratio: CropRatio,
    val quarterTurns: Int,
    val angle: Double,
    val scale: Double,
    val focus: CropPoint,
) {
    val windowSize: CropSize get() = CropSize(ratio.value, 1.0)

    /** Total clockwise turn, radians. */
    val theta: Double get() = Math.toRadians(quarterTurns * 90.0 + angle)

    /** The smallest scale at which the turned image covers the window. */
    val minScale: Double
        get() {
            val c = abs(cos(theta))
            val s = abs(sin(theta))
            val w = windowSize.width
            val h = windowSize.height
            val byWidth = (w * c + h * s) / imageSize.width
            val byHeight = (w * s + h * c) / imageSize.height
            return max(byWidth, byHeight)
        }

    val maxScale: Double get() = minScale * MAX_ZOOM

    /** Half the window's extent along the image's own axes, in image pixels. */
    private fun halfExtentsInImage(scale: Double): CropSize {
        val c = abs(cos(theta))
        val s = abs(sin(theta))
        val a = windowSize.width / 2
        val b = windowSize.height / 2
        return CropSize((a * c + b * s) / scale, (a * s + b * c) / scale)
    }

    /**
     * Where the photo's centre sits relative to the window centre, in window units: what the canvas
     * offsets the rotated, scaled image by.
     */
    val windowOffset: CropPoint get() = CropPoint(-focus.x * scale, -focus.y * scale).rotated(theta)

    /** The window's four corners taken back into image pixels (from the image centre). */
    fun windowCornersInImage(): List<CropPoint> {
        val a = windowSize.width / 2
        val b = windowSize.height / 2
        return listOf(CropPoint(-a, -b), CropPoint(a, -b), CropPoint(a, b), CropPoint(-a, b)).map { corner ->
            val r = corner.rotated(-theta)
            CropPoint(focus.x + r.x / scale, focus.y + r.y / scale)
        }
    }

    // Clamping

    /**
     * Angle and turns in range, scale between covering and the zoom cap, and the focus pulled in
     * until every window corner is on the photo.
     */
    fun normalized(): CropGeometry {
        val turned =
            copy(
                quarterTurns = ((quarterTurns % 4) + 4) % 4,
                angle = angle.coerceIn(-MAX_ANGLE, MAX_ANGLE),
            )
        val scaled = turned.copy(scale = min(max(scale, turned.minScale), turned.maxScale))
        return scaled.copy(focus = scaled.clampedFocus(scaled.focus))
    }

    private fun clampedFocus(point: CropPoint): CropPoint {
        val half = halfExtentsInImage(scale)
        val limitX = max(imageSize.width / 2 - half.width, 0.0)
        val limitY = max(imageSize.height / 2 - half.height, 0.0)
        return CropPoint(point.x.coerceIn(-limitX, limitX), point.y.coerceIn(-limitY, limitY))
    }

    /** While a drag runs past the edge: the photo follows a share of the overshoot, so the edge resists instead of stopping dead. */
    fun rubberBanded(): CropGeometry {
        val g = copy(scale = min(max(scale, minScale), maxScale))
        val clamped = g.clampedFocus(focus)
        return g.copy(
            focus =
                CropPoint(
                    clamped.x + (focus.x - clamped.x) * RUBBER_BAND_FACTOR,
                    clamped.y + (focus.y - clamped.y) * RUBBER_BAND_FACTOR,
                ),
        )
    }

    // Edits

    /** The photo follows the finger: a drag of [translation] window units moves the focus the opposite way, in image pixels. Not clamped. */
    fun panned(translation: CropPoint): CropGeometry {
        val d = translation.rotated(-theta)
        return copy(focus = CropPoint(focus.x - d.x / scale, focus.y - d.y / scale))
    }

    /** A pinch about the window centre. Not clamped. */
    fun zoomed(factor: Double): CropGeometry = copy(scale = scale * factor)

    /** Spec D6: same focus, the scale raised only if the new angle needs it. */
    fun withAngle(degrees: Double): CropGeometry = copy(angle = degrees).normalized()

    fun withRatio(newRatio: CropRatio): CropGeometry = copy(ratio = newRatio).normalized()

    /** Spec D5: a clockwise quarter turn, the format flipped to its pair. */
    fun turnedQuarter(): CropGeometry = copy(quarterTurns = quarterTurns + 1, ratio = ratio.paired).normalized()

    companion object {
        const val MAX_ANGLE = 45.0

        /** How far past the smallest covering scale a pinch may go. */
        const val MAX_ZOOM = 8.0

        /** The share of an overshoot the rubber band shows while dragging. */
        const val RUBBER_BAND_FACTOR = 0.3

        /** The editor's opening state (spec D3): the closest format, centred, covering. */
        fun initial(imageSize: CropSize): CropGeometry {
            val ratio = CropRatio.closest(imageSize.width / max(imageSize.height, 1.0))
            val g = CropGeometry(imageSize, ratio, quarterTurns = 0, angle = 0.0, scale = 0.0, focus = CropPoint.Zero)
            return g.copy(scale = g.minScale)
        }
    }
}

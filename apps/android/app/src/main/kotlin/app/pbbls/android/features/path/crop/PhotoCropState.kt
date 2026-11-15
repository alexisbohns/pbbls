package app.pbbls.android.features.path.crop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.pbbls.android.core.data.CropSource
import app.pbbls.android.core.model.crop.CropGeometry
import app.pbbls.android.core.model.crop.CropPoint
import app.pbbls.android.core.model.crop.CropRatio
import app.pbbls.android.core.model.crop.CropSize

/** The editor's tools, in the group's order. Filters will be a third entry (spec "Tabs"). */
enum class CropTool { RATIO, ROTATE }

/**
 * The crop editor's state (#1659, iOS `PhotoCropModel`). Held by the flow's ViewModel inside its
 * [CropSession], so a rotation keeps the framing; it is Compose state because the editor draws it.
 *
 * Gestures arrive as whole-gesture totals (translation, magnification) applied to the geometry the
 * gesture started from, so pan and pinch together never fight over one value.
 */
class PhotoCropState(
    imageWidth: Int,
    imageHeight: Int,
    initialTool: CropTool = CropTool.RATIO,
) {
    private val initial = CropGeometry.initial(CropSize(imageWidth.toDouble(), imageHeight.toDouble()))

    var geometry: CropGeometry by mutableStateOf(initial)
        private set

    var tool: CropTool by mutableStateOf(initialTool)

    /** True while a finger is on the canvas: the thirds grid shows. */
    var isInteracting: Boolean by mutableStateOf(false)
        private set

    /** True while the ruler is dragged: the finer grid shows. */
    var isRotating: Boolean by mutableStateOf(false)

    private var gestureStart: CropGeometry? = null

    /** Reset shows only once something changed. */
    val hasChanges: Boolean get() = geometry != initial

    fun select(ratio: CropRatio) {
        geometry = geometry.withRatio(ratio)
    }

    fun setAngle(degrees: Double) {
        geometry = geometry.withAngle(degrees)
    }

    fun turnQuarter() {
        geometry = geometry.turnedQuarter()
    }

    fun reset() {
        geometry = initial
    }

    /** Accessibility zoom: one step, clamped. */
    fun zoom(factor: Double) {
        geometry = geometry.zoomed(factor).normalized()
    }

    /**
     * [translationX]/[translationY] and [magnification] are totals since the gesture began; the
     * translation is in pixels on a window [windowHeightPx] tall.
     */
    fun gestureChanged(
        translationX: Float,
        translationY: Float,
        magnification: Float,
        windowHeightPx: Float,
    ) {
        val start = gestureStart ?: geometry
        gestureStart = start
        isInteracting = true
        val units = CropPoint(translationX / windowHeightPx.toDouble(), translationY / windowHeightPx.toDouble())
        // Clamp the pinch first: panning divides by the scale, so an over-pinch would otherwise move
        // the photo faster or slower than the finger.
        val zoomed = start.zoomed(magnification.toDouble())
        val clamped = zoomed.copy(scale = zoomed.scale.coerceIn(zoomed.minScale, zoomed.maxScale))
        geometry = clamped.panned(units).rubberBanded()
    }

    /** The settled geometry a release springs to. Safe to call twice (a finished and a cancelled gesture both end here). */
    fun gestureEnded(): CropGeometry {
        gestureStart = null
        isInteracting = false
        return geometry.normalized()
    }

    /** One frame of the settle after a release: the editor animates from the banded geometry to [gestureEnded]'s. */
    fun showSettling(frame: CropGeometry) {
        geometry = frame
    }
}

/**
 * A loaded photo, the gate token it belongs to and its editor state. It rides from the load, through
 * waiting for a free screen, into the editor, so Done and Cancel release the pick they were opened for
 * and no other (iOS `CropSession`).
 */
class CropSession(
    val source: CropSource,
    val token: Long,
) {
    val state = PhotoCropState(source.image.width, source.image.height)
}

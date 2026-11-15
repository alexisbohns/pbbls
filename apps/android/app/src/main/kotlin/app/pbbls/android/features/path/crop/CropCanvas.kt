package app.pbbls.android.features.path.crop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.model.crop.CropGeometry
import app.pbbls.android.core.model.crop.CropPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The surface-coloured scrim over the photo outside the window. */
private const val SCRIM_ALPHA = 0.78f

private const val GRID_ALPHA = 0.6f

/** The accessibility zoom step, in and out. */
private const val ZOOM_STEP = 1.25

/**
 * The photo under a fixed window (#1659, iOS `CropCanvas`, spec "Canvas"). The image is placed by the
 * same chain the renderer uses (offset, turn, scale), so what is in the window is exactly what Done
 * keeps. Drag pans, pinch zooms; past the edge the photo rubber-bands and settles inside on release.
 * A thirds grid shows while touching, a finer one while straightening.
 */
@Composable
fun CropCanvas(
    state: PhotoCropState,
    image: ImageBitmap,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val settle = remember { arrayOfNulls<Job>(1) }
    val gutter = with(LocalDensity.current) { Spacing.lg.toPx() }
    val ratio by animateFloatAsState(
        targetValue =
            state.geometry.ratio.value
                .toFloat(),
        animationSpec = if (reduceMotion) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "cropWindowRatio",
    )
    val currentRatio by rememberUpdatedState(ratio)
    val currentReduceMotion by rememberUpdatedState(reduceMotion)
    val photo = stringResource(R.string.photo_label)
    val zoomIn = stringResource(R.string.crop_zoom_in)
    val zoomOut = stringResource(R.string.crop_zoom_out)

    Canvas(
        modifier
            .clipToBounds()
            .clearAndSetSemantics {
                contentDescription = photo
                customActions =
                    listOf(
                        CustomAccessibilityAction(zoomIn) {
                            state.zoom(ZOOM_STEP)
                            true
                        },
                        CustomAccessibilityAction(zoomOut) {
                            state.zoom(1 / ZOOM_STEP)
                            true
                        },
                    )
            }.pointerInput(state) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    // A new touch takes over from a settle still running.
                    settle[0]?.cancel()
                    val windowHeight = windowRect(Size(size.width.toFloat(), size.height.toFloat()), currentRatio, gutter).height
                    var pan = Offset.Zero
                    var zoom = 1f
                    state.gestureChanged(0f, 0f, 1f, windowHeight)
                    do {
                        val event = awaitPointerEvent()
                        pan += event.calculatePan()
                        zoom *= event.calculateZoom()
                        state.gestureChanged(pan.x, pan.y, zoom, windowHeight)
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    } while (event.changes.any { it.pressed })
                    // Also reached when the system cancels the touch.
                    val from = state.geometry
                    val target = state.gestureEnded()
                    if (currentReduceMotion || from == target) {
                        state.showSettling(target)
                    } else {
                        settle[0] =
                            scope.launch {
                                val progress = Animatable(0f)
                                progress.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) {
                                    state.showSettling(interpolate(from, target, value.toDouble()))
                                }
                            }
                    }
                }
            },
    ) {
        val window = windowRect(size, ratio, gutter)
        drawPhoto(image, state.geometry, window)
        val scrim =
            Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addRect(window)
            }
        drawPath(scrim, colors.surface.copy(alpha = SCRIM_ALPHA))
        if (state.isInteracting || state.isRotating) {
            drawGrid(window, if (state.isRotating) 6 else 3, colors.surface.copy(alpha = GRID_ALPHA))
        }
        drawRect(colors.outlineVariant, window.topLeft, window.size, style = Stroke(1.dp.toPx()))
    }
}

/** The largest window of the format that fits with a gutter, centred. */
private fun windowRect(
    size: Size,
    ratio: Float,
    gutter: Float,
): Rect {
    val availableWidth = maxOf(size.width - gutter * 2, 1f)
    val availableHeight = maxOf(size.height - gutter * 2, 1f)
    val height = minOf(availableHeight, availableWidth / ratio)
    val width = height * ratio
    return Rect(Offset((size.width - width) / 2, (size.height - height) / 2), Size(width, height))
}

private fun DrawScope.drawPhoto(
    image: ImageBitmap,
    g: CropGeometry,
    window: Rect,
) {
    val unit = window.height
    val pixel = (g.scale * unit).toFloat()
    withTransform({
        translate(window.center.x + (g.windowOffset.x * unit).toFloat(), window.center.y + (g.windowOffset.y * unit).toFloat())
        rotate(Math.toDegrees(g.theta).toFloat(), pivot = Offset.Zero)
        scale(pixel, pixel, pivot = Offset.Zero)
        translate(-image.width / 2f, -image.height / 2f)
    }) {
        drawImage(image, filterQuality = FilterQuality.High)
    }
}

/** Lines dividing [window] into [divisions] × [divisions]. */
private fun DrawScope.drawGrid(
    window: Rect,
    divisions: Int,
    color: androidx.compose.ui.graphics.Color,
) {
    val stroke = 0.75.dp.toPx()
    for (i in 1 until divisions) {
        val x = window.left + window.width * i / divisions
        val y = window.top + window.height * i / divisions
        drawLine(color, Offset(x, window.top), Offset(x, window.bottom), stroke)
        drawLine(color, Offset(window.left, y), Offset(window.right, y), stroke)
    }
}

/** A frame of the settle: focus and scale eased from [from] to [to]; the turn and format do not move. */
private fun interpolate(
    from: CropGeometry,
    to: CropGeometry,
    t: Double,
): CropGeometry =
    to.copy(
        scale = from.scale + (to.scale - from.scale) * t,
        focus = CropPoint(from.focus.x + (to.focus.x - from.focus.x) * t, from.focus.y + (to.focus.y - from.focus.y) * t),
    )

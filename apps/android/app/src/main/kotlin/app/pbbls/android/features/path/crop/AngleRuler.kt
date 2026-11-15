package app.pbbls.android.features.path.crop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.common.SeededGenerator
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.designsystem.SpineTypography
import app.pbbls.android.core.model.crop.CropGeometry
import kotlin.math.abs
import kotlin.math.roundToInt

private val PerDegree = 8.dp
private val RulerHeight = 44.dp
private val NeedleWidth = 3.dp
private val NeedleHeight = 40.dp

/** Within this many degrees of 0 a drag snaps to it. */
private const val SNAP_WITHIN = 0.75

/** How far in from each side the ticks fade out, as a share of the width. */
private const val FADE_SHARE = 0.22f

private const val MAX_DEGREES = 45

/**
 * The straighten ruler (#1659, iOS `AngleRuler`): −45…45°, ticks drawn by hand under a fixed
 * `primary` needle. A haptic per whole degree, a snap at 0°, double-tap to return to 0°. An
 * adjustable control for TalkBack, 1° per step.
 *
 * The ticks and the drag are physical, not mirrored in RTL: the ruler measures a clockwise turn, which
 * is the same on screen in every locale.
 */
@Composable
fun AngleRuler(
    angle: Double,
    onChange: (Double) -> Unit,
    onEditingChange: (Boolean) -> Unit,
    onDegreeTick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val currentAngle by rememberUpdatedState(angle)
    val currentOnChange by rememberUpdatedState(onChange)
    val currentOnEditing by rememberUpdatedState(onEditingChange)
    val perDegreePx = with(LocalDensity.current) { PerDegree.toPx() }
    val whole = angle.roundToInt()
    val label = stringResource(R.string.crop_angle)
    val valueText = pluralStringResource(R.plurals.crop_angle_degrees, abs(whole), whole)

    // A tick per whole degree crossed, not on the first frame.
    val lastWhole = remember { intArrayOf(whole) }
    LaunchedEffect(whole) {
        if (lastWhole[0] != whole) onDegreeTick()
        lastWhole[0] = whole
    }

    Column(
        modifier =
            modifier.clearAndSetSemantics {
                contentDescription = label
                stateDescription = valueText
                progressBarRangeInfo =
                    ProgressBarRangeInfo(whole.toFloat(), -MAX_DEGREES.toFloat()..MAX_DEGREES.toFloat(), 2 * MAX_DEGREES - 1)
                setProgress { target ->
                    currentOnChange(target.roundToInt().toDouble().coerceIn(-CropGeometry.MAX_ANGLE, CropGeometry.MAX_ANGLE))
                    true
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(text = "$whole°", style = SpineTypography.entryTime, color = colors.onSurface)
        val major = colors.onSurface
        val minor = colors.onSurfaceVariant
        val needle = colors.primary
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(RulerHeight)
                .pointerInput(perDegreePx) {
                    var start = 0.0
                    var travelled = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            start = currentAngle
                            travelled = 0f
                            currentOnEditing(true)
                        },
                        onDragEnd = { currentOnEditing(false) },
                        onDragCancel = { currentOnEditing(false) },
                    ) { change, dx ->
                        change.consume()
                        travelled += dx
                        // Ticks slide under the needle: dragging left raises the angle.
                        var next = (start - travelled / perDegreePx).coerceIn(-CropGeometry.MAX_ANGLE, CropGeometry.MAX_ANGLE)
                        if (abs(next) < SNAP_WITHIN) next = 0.0
                        currentOnChange(next)
                    }
                }.pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { currentOnChange(0.0) })
                },
        ) {
            val centreX = size.width / 2 - angle.toFloat() * perDegreePx
            val fade = size.width * FADE_SHARE
            for (degree in -MAX_DEGREES..MAX_DEGREES) {
                val slot = centreX + degree * perDegreePx
                if (slot < -5f || slot > size.width + 5f) continue
                val rng = SeededGenerator("angle-tick-$degree")
                val isMajor = degree % 15 == 0
                val isMid = degree % 5 == 0
                val base =
                    when {
                        degree == 0 -> 30.dp
                        isMajor -> 24.dp
                        isMid -> 18.dp
                        else -> 11.dp
                    }.toPx()
                val length = base * (1 + rng.unit().toFloat() * 0.1f)
                val x = slot + rng.unit().toFloat() * 0.6f.dp.toPx()
                val lean = rng.unit().toFloat() * 0.9f.dp.toPx()
                val bottom = size.height / 2 + length / 2
                // The ticks fade out toward both ends instead of stopping at a hard edge.
                val alpha = (minOf(slot, size.width - slot) / fade).coerceIn(0f, 1f)
                val width =
                    when {
                        degree == 0 -> 2.5.dp
                        isMid -> 1.8.dp
                        else -> 1.3.dp
                    }.toPx()
                drawLine(
                    color = if (degree == 0 || isMajor) major else minor,
                    start = Offset(x - lean, bottom),
                    end = Offset(x + lean, bottom - length),
                    strokeWidth = width,
                    cap = StrokeCap.Round,
                    alpha = alpha,
                )
            }
            val needleW = NeedleWidth.toPx()
            val needleH = NeedleHeight.toPx()
            drawRoundRect(
                color = needle,
                topLeft = Offset(size.width / 2 - needleW / 2, size.height / 2 - needleH / 2),
                size = Size(needleW, needleH),
                cornerRadius = CornerRadius(needleW / 2),
            )
        }
    }
}

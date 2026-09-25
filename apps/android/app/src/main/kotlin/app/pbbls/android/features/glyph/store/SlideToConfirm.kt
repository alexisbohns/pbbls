package app.pbbls.android.features.glyph.store

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.distanceFromStart
import app.pbbls.android.core.designsystem.logicalDelta
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure slide geometry — ports iOS `SlideMath` so the thresholds are
 * JVM-tested: travel excludes the thumb, confirm at 90% of travel.
 */
object SlideMath {
    const val CONFIRM_THRESHOLD = 0.9f

    fun travel(
        trackWidth: Float,
        thumb: Float,
    ): Float = max(1f, trackWidth - thumb)

    fun progress(
        dragX: Float,
        travel: Float,
    ): Float = dragX.coerceIn(0f, travel) / travel

    fun isConfirmed(
        progress: Float,
        threshold: Float = CONFIRM_THRESHOLD,
    ): Boolean = progress >= threshold
}

/**
 * Slide-to-confirm purchase control — ports iOS `SlideToConfirm` (M43 D6):
 * 56dp thumb carrying the karma cost, drag engages only when the press starts
 * on the resting thumb, a `surfaceContainerLowest` trail wipes the
 * `primaryContainer` track as the drag grows, confirm at 0.9 ×
 * travel parks the thumb (springing back when [onConfirm] returns false).
 * Feedback is haptic-only v1 (the audio half is a named deviation): a
 * long-press tick on engage, confirm on the threshold — fired BEFORE the RPC,
 * verbatim iOS quirk. TalkBack's double-tap runs the same confirm directly (a
 * button with the cost as its state, mirroring iOS's activate action), and the
 * drag reads logical x, so in RTL the thumb starts at the right and slides left.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SlideToConfirm(
    cost: Int,
    enabled: Boolean,
    onConfirm: suspend () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }
    var trackWidthPx by remember { mutableIntStateOf(0) }
    val thumbPx = with(density) { THUMB.toPx() }
    val layoutDirection = LocalLayoutDirection.current
    val a11y = stringResource(R.string.glyph_drawer_slide_a11y)
    val confirmAction = stringResource(R.string.glyph_drawer_slide_confirm_action)
    val costA11y = pluralStringResource(R.plurals.glyph_drawer_slide_cost_a11y, cost, cost)

    // The drag and TalkBack's double-tap end in the same place: park the
    // thumb, run the purchase, spring back if it failed. The haptic fires
    // before the RPC — verbatim iOS quirk.
    fun confirm(travel: Float) {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        scope.launch {
            dragX.animateTo(travel)
            val success = onConfirm()
            if (!success) dragX.animateTo(0f)
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(THUMB)
                .onSizeChanged { trackWidthPx = it.width }
                .clip(CircleShape)
                .background(colors.primaryContainer)
                .alpha(if (enabled) 1f else 0.5f)
                .clearAndSetSemantics {
                    contentDescription = a11y
                    stateDescription = costA11y
                    role = Role.Button
                    if (enabled) {
                        onClick(label = confirmAction) {
                            confirm(SlideMath.travel(trackWidthPx.toFloat(), thumbPx))
                            true
                        }
                    } else {
                        disabled()
                    }
                }.pointerInput(enabled, trackWidthPx, layoutDirection) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        // The press must start on the resting thumb, which sits at the start edge.
                        if (layoutDirection.distanceFromStart(down.position.x, trackWidthPx.toFloat()) > thumbPx) {
                            return@awaitEachGesture
                        }
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val travel = SlideMath.travel(trackWidthPx.toFloat(), thumbPx)
                        drag(down.id) { change ->
                            val delta = layoutDirection.logicalDelta(change.position.x - change.previousPosition.x)
                            val next = (dragX.value + delta).coerceIn(0f, travel)
                            scope.launch { dragX.snapTo(next) }
                            change.consume()
                        }
                        if (SlideMath.isConfirmed(SlideMath.progress(dragX.value, travel))) {
                            confirm(travel)
                        } else {
                            scope.launch { dragX.animateTo(0f) }
                        }
                    }
                },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .width(with(density) { (dragX.value + thumbPx).toDp() })
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colors.surfaceContainerLowest),
        )
        Text(
            text = stringResource(R.string.glyph_drawer_slide),
            style = MaterialTheme.typography.bodyMediumEmphasized,
            color = colors.onPrimaryContainer,
            modifier = Modifier.align(Alignment.Center),
        )
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(dragX.value.roundToInt(), 0) }
                    .size(THUMB)
                    .clip(CircleShape)
                    .background(colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = cost.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onPrimary,
            )
        }
    }
}

private val THUMB = 56.dp

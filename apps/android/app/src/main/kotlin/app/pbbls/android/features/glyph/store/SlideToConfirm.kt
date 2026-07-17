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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
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
 * on the resting thumb, the accent fill grows with the drag, confirm at 0.9 ×
 * travel parks the thumb (springing back when [onConfirm] returns false).
 * Feedback is haptic-only v1 (the audio half is a named deviation): a
 * long-press tick on engage, confirm on the threshold — fired BEFORE the RPC,
 * verbatim iOS quirk.
 */
@Composable
fun SlideToConfirm(
    cost: Int,
    enabled: Boolean,
    onConfirm: suspend () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = PebblesTheme.colors.accent
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }
    var trackWidthPx by remember { mutableIntStateOf(0) }
    val thumbPx = with(density) { THUMB.toPx() }
    val a11y = stringResource(R.string.glyph_drawer_slide_a11y)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(THUMB)
                .onSizeChanged { trackWidthPx = it.width }
                .clip(RoundedCornerShape(50))
                .background(accent.surface)
                .alpha(if (enabled) 1f else 0.5f)
                .clearAndSetSemantics { contentDescription = a11y }
                .pointerInput(enabled, trackWidthPx) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (down.position.x > thumbPx) return@awaitEachGesture
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val travel = SlideMath.travel(trackWidthPx.toFloat(), thumbPx)
                        drag(down.id) { change ->
                            val delta = change.position.x - change.previousPosition.x
                            val next = (dragX.value + delta).coerceIn(0f, travel)
                            scope.launch { dragX.snapTo(next) }
                            change.consume()
                        }
                        val progress = SlideMath.progress(dragX.value, travel)
                        if (SlideMath.isConfirmed(progress)) {
                            // iOS fires success feedback at the threshold, before the RPC.
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            scope.launch {
                                dragX.animateTo(travel)
                                val success = onConfirm()
                                if (!success) dragX.animateTo(0f)
                            }
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
                    .clip(RoundedCornerShape(50))
                    .background(accent.light),
        )
        PebblesText(
            text = stringResource(R.string.glyph_drawer_slide),
            style = PebblesTypography.subheadEmphasized,
            color = accent.primary,
            modifier = Modifier.align(Alignment.Center),
        )
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(dragX.value.roundToInt(), 0) }
                    .size(THUMB)
                    .clip(CircleShape)
                    .background(accent.primary),
            contentAlignment = Alignment.Center,
        ) {
            PebblesText(
                text = cost.toString(),
                style = PebblesTypography.headline,
                color = Color.White,
            )
        }
    }
}

private val THUMB = 56.dp

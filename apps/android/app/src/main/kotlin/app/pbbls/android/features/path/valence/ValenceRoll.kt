package app.pbbls.android.features.path.valence

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.pbbls.android.features.path.models.Valence
import app.pbbls.android.features.path.models.ValencePolarity
import app.pbbls.android.features.path.models.ValenceSizeGroup
import app.pbbls.android.features.path.models.polarityAfter
import app.pbbls.android.features.path.models.polarityBefore
import app.pbbls.android.features.path.models.polarityIndex
import app.pbbls.android.features.path.models.sizeIndex
import app.pbbls.android.features.path.models.valenceAt
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import app.pbbls.android.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Finger travel per step. Also the distance the neighbour words sit out at,
 * because the two have to agree for the roll to feel 1:1.
 */
private val PolarityStep = 220.dp
private val SizeStep = 90.dp

/** How far past the last step the content will stretch. */
private val Overscroll = 34.dp
private const val NEIGHBOUR_OPACITY = 0.22f

/** Height the lit-mark pyramid draws at, and the gap between its marks. */
private val MarkHeight = 6.dp

private enum class RollAxis { POLARITY, SIZE }

/**
 * The lockup under the fan, as a two-axis roll: swipe left and right to change
 * polarity, up and down to change size — ports iOS `ValenceRollView.swift`.
 *
 * The roll is 1:1 with the finger (the content travels exactly as far as the
 * hand does) and detents at the half step, so the answer changes under the
 * thumb rather than on release. Each detent springs the new value to centre and
 * buzzes, which is what makes it read as magnetic rather than as a slider. The
 * ends clamp instead of wrapping, with rubber-band resistance past the last
 * step, so a hard swipe cannot loop the user back where they started.
 *
 * Nothing moves that is not changing. The block is anchored to its bottom edge
 * and the pyramid is always three marks tall, so rolling between sizes never
 * shifts the layout: the word springs its size in place and the pyramid lights
 * a different mark. On the polarity axis only the word row travels — the span
 * reads the same for all three polarities, so sliding it would be motion that
 * says nothing.
 *
 * **The roll plays no haptic of its own.** iOS's does, and its record step
 * buzzes again through the model; here the flow's rule that every interaction
 * goes through `RecordFlowModel` (and that every method there buzzes) already
 * covers the detent, so a second buzz in the view would read as a stutter. The
 * hosts that do not go through the model — the edit sheet — buzz in their own
 * `onChange`.
 */
@Composable
internal fun ValenceRoll(
    valence: Valence,
    onChange: (Valence) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.colors
    val current by rememberUpdatedState(valence)
    val change by rememberUpdatedState(onChange)
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val wordScale by animateFloatAsState(
        targetValue = valenceWordScale(valence.sizeGroup),
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 580f),
        label = "valenceWordScale",
    )

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}
                // A plain drag gesture is enough to beat the step's scroll
                // container: `detectDragGestures` consumes every change it
                // handles, and Compose dispatches to the child first, so the
                // parent never sees the vertical drag the size axis needs.
                .pointerInput(Unit) {
                    val polarityStepPx = PolarityStep.toPx()
                    val sizeStepPx = SizeStep.toPx()
                    val overscrollPx = Overscroll.toPx()
                    // Captured when a drag starts: every frame resolves against
                    // where the roll was when the finger landed, never against
                    // the value it has drifted to.
                    var origin = current
                    // What the roll has actually reported, tracked here rather
                    // than read back off the state: several pointer events can
                    // land between two recompositions, and comparing against a
                    // value that has not caught up yet would report the same
                    // detent twice — and buzz twice for it.
                    var emitted = current
                    var axis: RollAxis? = null
                    var travel = Offset.Zero

                    detectDragGestures(
                        onDragStart = {
                            origin = current
                            emitted = current
                            axis = null
                            travel = Offset.Zero
                        },
                        onDragEnd = {
                            axis = null
                            scope.launch {
                                offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 340f))
                            }
                        },
                        onDragCancel = {
                            axis = null
                            scope.launch {
                                offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 340f))
                            }
                        },
                        onDrag = { _, delta ->
                            travel += delta
                            // The axis is decided once per drag and held.
                            // Without the lock a diagonal swipe alternates axes
                            // frame to frame and the roll shakes instead of
                            // rolling.
                            val locked =
                                axis ?: (
                                    if (abs(travel.x) > abs(travel.y)) RollAxis.POLARITY else RollAxis.SIZE
                                ).also { axis = it }

                            val amount = if (locked == RollAxis.POLARITY) travel.x else travel.y
                            val step = if (locked == RollAxis.POLARITY) polarityStepPx else sizeStepPx

                            // Content follows the finger, so dragging left
                            // brings the value on the right to centre: the index
                            // moves against the travel.
                            val next = destination(origin, locked, (-amount / step).roundToInt())
                            if (next != emitted) {
                                emitted = next
                                change(next)
                            }

                            // Whatever travel the steps did not consume is what
                            // the content is still holding, so it eases back to
                            // centre as each detent passes and stretches when
                            // there is nothing left to move to.
                            val taken =
                                if (locked == RollAxis.POLARITY) {
                                    next.polarityIndex - origin.polarityIndex
                                } else {
                                    next.sizeIndex - origin.sizeIndex
                                }
                            val remainder = amount + taken * step
                            val target =
                                if (locked == RollAxis.POLARITY) {
                                    rubberBanded(remainder, step / 2f, overscrollPx)
                                } else {
                                    // The size axis deliberately does not
                                    // translate: a block that slid vertically
                                    // would drag the whole lockup past its
                                    // neighbours, and the detent plus the
                                    // pyramid already say what changed.
                                    0f
                                }
                            scope.launch { offset.snapTo(target) }
                        },
                    )
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        // The only part that travels on the polarity axis. Nothing clips it, so
        // the neighbour words bleed off the screen edges as designed.
        Box(
            modifier = Modifier.fillMaxWidth().offset { IntOffset(offset.value.roundToInt(), 0) },
            contentAlignment = Alignment.BottomCenter,
        ) {
            valence.polarityBefore?.let { Neighbour(it, valence.sizeGroup, wordScale, -PolarityStep) }
            valence.polarityAfter?.let { Neighbour(it, valence.sizeGroup, wordScale, PolarityStep) }
            ValenceWord(valence = valence, scale = wordScale)
        }

        PebblesText(
            text = stringResource(valenceSpanRes(valence.sizeGroup)),
            style = PebblesTypography.cardHeading,
            color = colors.system.secondary,
            textAlign = TextAlign.Center,
        )

        Pyramid(current = valence.sizeGroup, modifier = Modifier.padding(top = Spacing.sm))
    }
}

/**
 * A neighbouring polarity, one step out. Rendered at the current size so the
 * row reads as one line of type, and faded so it never competes with the
 * answer.
 */
@Composable
private fun Neighbour(
    polarity: ValencePolarity,
    size: ValenceSizeGroup,
    scale: Float,
    distance: Dp,
) {
    ValenceWord(
        valence = valenceAt(polarity, size),
        scale = scale,
        modifier = Modifier.offset(x = distance).alpha(NEIGHBOUR_OPACITY),
    )
}

/**
 * Three marks, widest at the top, with the current size lit. Always all three:
 * a pyramid that changed height would move everything above it, which is the
 * shift this layout exists to avoid.
 */
@Composable
private fun Pyramid(
    current: ValenceSizeGroup,
    modifier: Modifier = Modifier,
) {
    val accent = PebblesTheme.colors.accent
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MarkHeight),
    ) {
        ValenceSizeGroup.ladder.forEach { size ->
            val lit by animateFloatAsState(
                targetValue = if (size == current) 1f else 0.25f,
                animationSpec = spring(stiffness = 1400f),
                label = "valenceMark",
            )
            Box(
                modifier =
                    Modifier
                        .size(width = markWidth(size), height = MarkHeight)
                        .alpha(lit)
                        .background(accent.primary, CircleShape),
            )
        }
    }
}

private fun markWidth(size: ValenceSizeGroup): Dp =
    when (size) {
        ValenceSizeGroup.SMALL -> 8.dp
        ValenceSizeGroup.MEDIUM -> 26.dp
        ValenceSizeGroup.LARGE -> 44.dp
    }

/** Where [steps] along [axis] lands, clamped to the grid. */
private fun destination(
    origin: Valence,
    axis: RollAxis,
    steps: Int,
): Valence =
    when (axis) {
        RollAxis.POLARITY -> valenceAt(origin.polarityIndex + steps, origin.sizeIndex)
        RollAxis.SIZE -> valenceAt(origin.polarityIndex, origin.sizeIndex + steps)
    }

/**
 * Travel past [limit] keeps moving, but at a quarter rate and capped, so the
 * end of the grid feels like a wall with give rather than a stop.
 */
private fun rubberBanded(
    value: Float,
    limit: Float,
    overscroll: Float,
): Float {
    if (abs(value) <= limit) return value
    val damped = min(overscroll, (abs(value) - limit) * 0.25f)
    return if (value < 0) -(limit + damped) else limit + damped
}

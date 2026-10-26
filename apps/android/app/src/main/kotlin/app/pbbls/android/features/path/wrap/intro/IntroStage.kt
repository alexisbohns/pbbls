package app.pbbls.android.features.path.wrap.intro

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import app.pbbls.android.core.designsystem.RevealMotion

/** The clock and the card a composable belongs to (#1242 D19). UI plumbing, like `LocalSharedTransitionScope`. */
@Stable
class IntroStage(
    val clock: IntroClock,
    val sequence: IntroSequence,
) {
    val reveal: IntroReveal = clock.reveal(sequence)
}

/** Null, the default, draws every figure settled (D2). */
val LocalIntroStage = compositionLocalOf<IntroStage?> { null }

/** The reveal of the card around this composable; [IntroReveal.settled] outside one. */
@Composable
@ReadOnlyComposable
fun currentIntroReveal(): IntroReveal = LocalIntroStage.current?.reveal ?: IntroReveal.settled

/**
 * Makes [content] a card of the intro: it registers [beats] with [clock] while composed (asked for only
 * as the card starts) and leaves as it goes. No clock: [content] settled, even inside another card. Either
 * way [content] stays in the same place, so a clock coming or going never remounts it.
 */
@Composable
fun IntroHost(
    clock: IntroClock?,
    sequence: IntroSequence,
    beats: () -> List<IntroBeat>,
    content: @Composable () -> Unit,
) {
    val current by rememberUpdatedState(beats)
    DisposableEffect(clock, sequence) {
        clock?.register(sequence) { current() }
        onDispose { clock?.leave(sequence) }
    }
    val stage = remember(clock, sequence) { clock?.let { IntroStage(it, sequence) } }
    CompositionLocalProvider(LocalIntroStage provides stage, content = content)
}

/** Frames [clock] while composed, active while [active]; leaving settles it (D20). The only way a page drives a clock. */
@Composable
fun IntroDrive(
    clock: IntroClock,
    active: Boolean,
) {
    LaunchedEffect(clock) { clock.drive() }
    SideEffect { clock.isActive = active }
    DisposableEffect(clock) { onDispose { clock.isActive = false } }
}

/** How a Compose part lands (iOS `IntroLanding`). */
enum class IntroLanding { RISE, FADE, POP }

/**
 * Lands this part on [key]'s beat, read in the draw phase. Settled, or with no key, it returns `this`:
 * no layer, today's render. Hidden parts keep their frames, so nothing jumps when they land.
 */
fun Modifier.introReveal(
    reveal: IntroReveal,
    key: IntroKey?,
    landing: IntroLanding = IntroLanding.RISE,
): Modifier =
    if (key == null || reveal === IntroReveal.settled) {
        this
    } else {
        graphicsLayer {
            val p = reveal(key).toDouble()
            when (landing) {
                IntroLanding.RISE -> {
                    val eased = RevealMotion.easeOut(p)
                    alpha = eased.toFloat()
                    translationY = IntroMotion.riseOffset.toPx() * (1 - eased).toFloat()
                }

                IntroLanding.FADE -> {
                    alpha = RevealMotion.easeOut(p).toFloat()
                }

                IntroLanding.POP -> {
                    alpha = RevealMotion.popOpacity(p).toFloat()
                    scaleX = RevealMotion.popScale(p).toFloat()
                    scaleY = scaleX
                }
            }
        }
    }

/**
 * Calls [onTap] for a tap anywhere inside. It watches the Initial pass and never consumes, so the tap still
 * reaches the child under it and does its job (a milestone opens its held card). A drag past the touch slop
 * is no tap, nor is a cancel: the system taking the gesture (a back swipe) releases the pointer already
 * consumed, in the Initial pass, where a child's own click has not consumed it yet. A pointer that vanishes
 * ends the gesture without a tap.
 */
fun Modifier.onAnyTap(onTap: () -> Unit): Modifier =
    pointerInput(onTap) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var tap = true
            while (true) {
                val change =
                    awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                        ?: return@awaitEachGesture
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) tap = false
                if (!change.pressed) {
                    if (change.isConsumed) tap = false
                    break
                }
            }
            if (tap) onTap()
        }
    }

package app.pbbls.android.features.path.wrap.intro

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
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
@Immutable
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
 * as the card starts) and leaves as it goes. No clock: [content] as it is.
 */
@Composable
fun IntroHost(
    clock: IntroClock?,
    sequence: IntroSequence,
    beats: () -> List<IntroBeat>,
    content: @Composable () -> Unit,
) {
    if (clock == null) {
        content()
        return
    }
    val current by rememberUpdatedState(beats)
    DisposableEffect(clock, sequence) {
        clock.register(sequence) { current() }
        onDispose { clock.leave(sequence) }
    }
    val stage = remember(clock, sequence) { IntroStage(clock, sequence) }
    CompositionLocalProvider(LocalIntroStage provides stage, content = content)
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
 * Calls [onTap] for a tap anywhere inside, observed and never consumed, so the tap still does its job
 * (a milestone opens its held card). A drag that a scroller takes, or past the touch slop, is no tap.
 */
fun Modifier.onAnyTap(onTap: () -> Unit): Modifier =
    pointerInput(onTap) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var tap = true
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) tap = false
                if (!change.pressed) break
            }
            if (tap) onTap()
        }
    }

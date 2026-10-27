package app.pbbls.android.features.path.wrap.intro

import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import java.time.YearMonth

/**
 * The month intro's haptics on this phone (#1242 D18): compositions where the vibrator plays primitives
 * (one per segment of a card, below), else one waveform per card where it controls amplitude, else
 * `performHapticFeedback` fired from the clock's frames. Every vibration is `USAGE_TOUCH`, so Android's
 * touch feedback setting governs it.
 * [enabled] is Pebbles' own switch (D23), asked at every vibration: a card's start, a landing tick, and each
 * frame that would fire a later segment or a tick. [forced] is the Lab's override.
 *
 * A new vibration replaces the current one at the vibrator service, which is why one [sounding] slot
 * suffices for tiers C and W. A landing [tick] never takes that slot: the clock's `landed` ticks only a
 * card that isn't playing.
 *
 * Tier C plays a card as segments ([IntroComposition.segments]): the first as the card starts, each later
 * one from the clock's frames on its scored time ([IntroSegmentQueue]), so a swell's train that plays short
 * or long on this vibrator never shifts what follows it.
 */
class IntroVibrator(
    private val vibrator: Vibrator?,
    private val view: View,
    private val enabled: () -> Boolean,
    forced: IntroHapticTier? = null,
) : IntroHapticPlaying {
    private val supported: Set<IntroPrimitiveKind> =
        vibrator
            ?.let { v ->
                IntroPrimitiveKind.entries.filter { v.areAllPrimitivesSupported(primitiveId(it)) }.toSet()
            }.orEmpty()

    private val durations: Map<IntroPrimitiveKind, Int> =
        vibrator
            ?.let { v ->
                // One kind per call: lint's WrongConstant can't see the constants through a spread IntArray.
                IntroPrimitiveKind.entries.associateWith { v.getPrimitiveDurations(primitiveId(it)).single() }
            }.orEmpty()

    val tier: IntroHapticTier =
        forced ?: IntroHapticTier.of(
            hasVibrator = vibrator?.hasVibrator() == true,
            tick = IntroPrimitiveKind.TICK in supported,
            amplitude = vibrator?.hasAmplitudeControl() == true,
        )

    /** Tier V's voices: each playing card's floored ticks, from its start. */
    private class Voice(
        val events: List<IntroHapticEvent>,
        val start: Long,
        var next: Int = 0,
    )

    private val voices = mutableMapOf<Pair<YearMonth, IntroSequence>, Voice>()

    /** Tier C: each playing card's segments after its first, fired from [frame]. */
    private val later = IntroSegmentQueue()

    /** Tiers C and W: the card whose vibration is sounding, and when its score ends (`System.nanoTime()`). */
    private var sounding: Pair<YearMonth, IntroSequence>? = null
    private var soundingEnd = 0L

    init {
        Log.d(TAG, "intro haptics tier $tier, primitives $supported")
    }

    override fun play(
        sequence: IntroSequence,
        month: YearMonth,
        beats: List<IntroBeat>,
        startNanos: Long,
    ) {
        if (!enabled()) return
        val leadMs = leadMs(startNanos)
        when (tier) {
            IntroHapticTier.COMPOSITION -> {
                val segments = IntroComposition.segments(beats, supported + IntroPrimitiveKind.TICK, durations, leadMs)
                // The later segments only follow a first that plays: [stop] cancels only what [sounding] names.
                if (vibrate { segments.firstOrNull()?.let { composition(it.primitives) } }) {
                    later.hold(sequence, month, startNanos, segments.drop(1))
                    sounding = month to sequence
                    soundingEnd = startNanos + (IntroScore.duration(beats) * 1e9).toLong()
                } else {
                    later.drop(sequence, month)
                }
            }
            IntroHapticTier.WAVEFORM ->
                if (vibrate { effectFor(beats, leadMs) }) {
                    sounding = month to sequence
                    soundingEnd = startNanos + (IntroScore.duration(beats) * 1e9).toLong()
                }
            IntroHapticTier.VIEW -> voices[month to sequence] = Voice(IntroHaptics.events(beats), startNanos)
            IntroHapticTier.NONE -> Unit
        }
    }

    /**
     * Stops this page's own voice only: settling September never cuts October's buzz (iOS `ownHaptics`).
     * A card that lands at its end rings out: only an interruption, before its score ends, cancels it.
     */
    override fun stop(
        sequence: IntroSequence,
        month: YearMonth,
    ) {
        voices.remove(month to sequence)
        later.drop(sequence, month)
        if (sounding == month to sequence) {
            if (System.nanoTime() < soundingEnd) vibrator?.cancel()
            sounding = null
        }
    }

    override fun tick(tick: IntroTick) {
        if (!enabled()) return
        when (tier) {
            IntroHapticTier.COMPOSITION, IntroHapticTier.WAVEFORM ->
                vibrate { effectFor(listOf(IntroBeat(IntroKey.Header, 0.0, 0.0, tick)), leadMs = 0) }
            IntroHapticTier.VIEW -> view.performHapticFeedback(constantFor(tick))
            IntroHapticTier.NONE -> Unit
        }
    }

    /**
     * Tier C fires the card's next segment a floor ahead of its start ([IntroSegmentQueue.SEGMENT_LEAD_NANOS]),
     * delayed to start on its scored time. It supersedes whatever still sounds: on a vibrator whose trains run
     * long, that cuts a train's tail. Tier V fires the ticks the frame crossed.
     *
     * With Pebbles' switch read off, the card stops as [stop] stops it: what sounds is cut, and what it still
     * held never fires, even once the switch is back on.
     */
    override fun frame(
        sequence: IntroSequence,
        month: YearMonth,
        nanos: Long,
    ) {
        if (!enabled()) return stop(sequence, month)
        later.due(sequence, month, nanos)?.let { segment -> vibrate { composition(segment.led(leadMs(segment.startNanos))) } }
        val voice = voices[month to sequence] ?: return
        val (fire, past) = IntroHaptics.crossed(voice.events, voice.next, (nanos - voice.start) / 1e9)
        voice.next = past
        fire?.let { view.performHapticFeedback(constantFor(it.tick)) }
    }

    /** Tiers C and W: [beats] as this tier's one vibration, or null when they play nothing. */
    private fun effectFor(
        beats: List<IntroBeat>,
        leadMs: Int,
    ): VibrationEffect? =
        when (tier) {
            IntroHapticTier.COMPOSITION ->
                composition(IntroComposition.of(beats, supported + IntroPrimitiveKind.TICK, durations, leadMs))
            IntroHapticTier.WAVEFORM -> {
                val wave = IntroWaveform.of(beats, leadMs)
                if (wave.timings.isEmpty()) {
                    null
                } else {
                    VibrationEffect.createWaveform(wave.timings.toLongArray(), wave.amplitudes.toIntArray(), -1)
                }
            }
            IntroHapticTier.VIEW, IntroHapticTier.NONE -> null
        }

    /** Tier C: [primitives] as one composition, or null when there are none. */
    private fun composition(primitives: List<IntroPrimitive>): VibrationEffect? =
        if (primitives.isEmpty()) {
            null
        } else {
            val composition = VibrationEffect.startComposition()
            primitives.forEach { composition.addPrimitive(primitiveId(it.kind), it.scale, it.pauseMs) }
            composition.compose()
        }

    /**
     * How long a vibration waits to start at [startNanos], from now. Clamped before narrowing: a virtual
     * timebase can't wrap it, nor pass the HAL's longest pause.
     */
    private fun leadMs(startNanos: Long): Int = ((startNanos - System.nanoTime()) / 1_000_000).coerceIn(0, MAX_LEAD_MS).toInt()

    /**
     * Builds the effect and plays it under one guard: building throws on a value the framework refuses
     * (a scale or a timing out of range), and that must never reach the main thread. True if it played.
     */
    private inline fun vibrate(build: () -> VibrationEffect?): Boolean {
        val v = vibrator ?: return false
        return try {
            val effect = build() ?: return false
            v.vibrate(effect, TOUCH)
            true
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "intro haptics refused", e)
            false
        } catch (e: IllegalStateException) {
            Log.e(TAG, "intro haptics refused", e)
            false
        }
    }

    private companion object {
        const val TAG = "intro"

        /** The longest lead a card's vibration waits for its frames. */
        const val MAX_LEAD_MS = 1_000L
        val TOUCH: VibrationAttributes = VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH).build()

        fun primitiveId(kind: IntroPrimitiveKind): Int =
            when (kind) {
                IntroPrimitiveKind.TICK -> VibrationEffect.Composition.PRIMITIVE_TICK
                IntroPrimitiveKind.LOW_TICK -> VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                IntroPrimitiveKind.CLICK -> VibrationEffect.Composition.PRIMITIVE_CLICK
            }

        /** Tier V: the constant by intensity (D18). */
        fun constantFor(tick: IntroTick): Int =
            when {
                tick.intensity < 0.35f -> HapticFeedbackConstants.CLOCK_TICK
                tick.intensity < 0.7f -> HapticFeedbackConstants.CONTEXT_CLICK
                else -> HapticFeedbackConstants.VIRTUAL_KEY
            }
    }
}

/** The intro's haptics for this view. [enabled] is Pebbles' switch, read live; [forced] the Lab's tier override. */
@Composable
fun rememberIntroVibrator(
    enabled: () -> Boolean = { true },
    forced: IntroHapticTier? = null,
): IntroVibrator {
    val context = LocalContext.current
    val view = LocalView.current
    val currentEnabled by rememberUpdatedState(enabled)
    return remember(context, view, forced) {
        IntroVibrator(context.getSystemService(VibratorManager::class.java)?.defaultVibrator, view, { currentEnabled() }, forced)
    }
}

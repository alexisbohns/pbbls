package app.pbbls.android.features.path.wrap.intro

import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import java.time.YearMonth

/**
 * The month intro's haptics on this phone (#1242 D18): one composition per card where the vibrator plays
 * primitives, else one waveform where it controls amplitude, else `performHapticFeedback` fired from the
 * clock's frames. Every vibration is `USAGE_TOUCH`, so Android's touch feedback setting governs it.
 * [enabled] is Pebbles' own switch (Part 8; on until then). [forced] is the Lab's override.
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

    /** Tiers C and W: the card whose vibration is sounding. */
    private var sounding: Pair<YearMonth, IntroSequence>? = null

    init {
        Log.i(TAG, "intro haptics tier $tier, primitives $supported")
    }

    override fun play(
        sequence: IntroSequence,
        month: YearMonth,
        beats: List<IntroBeat>,
        startNanos: Long,
    ) {
        if (!enabled()) return
        val leadMs = ((startNanos - System.nanoTime()) / 1_000_000).toInt().coerceAtLeast(0)
        when (tier) {
            IntroHapticTier.COMPOSITION -> {
                val prims = IntroComposition.of(beats, supported + IntroPrimitiveKind.TICK, durations, leadMs)
                if (prims.isEmpty()) return
                val composition = VibrationEffect.startComposition()
                prims.forEach { composition.addPrimitive(primitiveId(it.kind), it.scale, it.pauseMs) }
                vibrate(composition.compose())
                sounding = month to sequence
            }
            IntroHapticTier.WAVEFORM -> {
                val wave = IntroWaveform.of(beats, leadMs)
                if (wave.timings.isEmpty()) return
                vibrate(VibrationEffect.createWaveform(wave.timings.toLongArray(), wave.amplitudes.toIntArray(), -1))
                sounding = month to sequence
            }
            IntroHapticTier.VIEW -> voices[month to sequence] = Voice(IntroHaptics.events(beats), startNanos)
            IntroHapticTier.NONE -> Unit
        }
    }

    /** Stops this page's own voice only: settling September never cuts October's buzz (iOS `ownHaptics`). */
    override fun stop(
        sequence: IntroSequence,
        month: YearMonth,
    ) {
        voices.remove(month to sequence)
        if (sounding == month to sequence) {
            vibrator?.cancel()
            sounding = null
        }
    }

    override fun tick(tick: IntroTick) {
        if (!enabled()) return
        when (tier) {
            IntroHapticTier.VIEW -> view.performHapticFeedback(constantFor(tick))
            IntroHapticTier.NONE -> Unit
            else -> play(IntroSequence.End, NO_MONTH, listOf(IntroBeat(IntroKey.Header, 0.0, 0.0, tick)), System.nanoTime())
        }
    }

    override fun frame(
        sequence: IntroSequence,
        month: YearMonth,
        nanos: Long,
    ) {
        val voice = voices[month to sequence] ?: return
        val elapsed = (nanos - voice.start) / 1e9
        var fire: IntroHapticEvent? = null
        while (voice.next < voice.events.size && voice.events[voice.next].at <= elapsed) {
            fire = voice.events[voice.next]
            voice.next += 1
        }
        fire?.let { view.performHapticFeedback(constantFor(it.tick)) }
    }

    private fun vibrate(effect: VibrationEffect) {
        val v = vibrator ?: return
        try {
            v.vibrate(effect, TOUCH)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "intro haptics refused by the vibrator", e)
        }
    }

    private companion object {
        const val TAG = "intro"

        /** A landing tick's voice: no page's month. */
        val NO_MONTH: YearMonth = YearMonth.of(1970, 1)
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

/** The intro's haptics for this view. [enabled] is Pebbles' switch; [forced] the Lab's tier override. */
@Composable
fun rememberIntroVibrator(
    enabled: () -> Boolean = { true },
    forced: IntroHapticTier? = null,
): IntroVibrator {
    val context = LocalContext.current
    val view = LocalView.current
    return remember(context, view, forced) {
        IntroVibrator(context.getSystemService(VibratorManager::class.java)?.defaultVibrator, view, enabled, forced)
    }
}

package app.pbbls.android.features.path.wrap.intro

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** One vibration's on-off timings (ms) and amplitudes (0 off, 1…255 on), for `VibrationEffect.createWaveform`. */
data class IntroWave(
    val timings: List<Long>,
    val amplitudes: List<Int>,
)

/** Tier W (D18): a sequence's beats as one waveform, for a vibrator with amplitude control and no primitives. */
object IntroWaveform {
    const val PULSE_MS = 8
    const val STEP_MS = 10

    fun of(
        beats: List<IntroBeat>,
        leadMs: Int,
    ): IntroWave {
        // A segment's bounds, in ms from the wave's start.
        data class Segment(
            val start: Double,
            val end: Double,
            val amplitude: Int,
        )

        // A non-finite intensity (NaN passes coerceIn, and throws in roundToInt) plays at the least amplitude.
        fun amp(intensity: Float) = if (intensity.isFinite()) (intensity * 255).roundToInt().coerceIn(1, 255) else 1
        val pulses =
            IntroHaptics.events(beats).map {
                val start = it.at * 1000 + leadMs
                Segment(start, start + PULSE_MS, amp(it.tick.intensity))
            }
        val steps =
            IntroHaptics.swells(beats).flatMap { swell ->
                val count = maxOf(1, (swell.sounding * 1000 / STEP_MS).toInt())
                List(count) { k ->
                    val f = if (count > 1) k / (count - 1f) else 1f
                    val start = swell.at * 1000 + leadMs + k * STEP_MS
                    Segment(start, start + STEP_MS, amp(swell.swell.from + (swell.swell.to - swell.swell.from) * f))
                }
            }
        val timings = mutableListOf<Long>()
        val amplitudes = mutableListOf<Int>()
        // Each bound rounds on its own and the timings are their differences, so rounding never adds up along
        // the wave (as IntroComposition keeps a whole-ms end). A segment overlapping the one before is cut short.
        var cursor = 0L
        (pulses + steps).sortedBy { it.start }.forEach { s ->
            val start = s.start.roundToLong()
            val end = s.end.roundToLong()
            if (start > cursor) {
                timings += start - cursor
                amplitudes += 0
                cursor = start
            }
            if (end <= cursor) return@forEach
            timings += end - cursor
            amplitudes += s.amplitude
            cursor = end
        }
        return IntroWave(timings, amplitudes)
    }
}

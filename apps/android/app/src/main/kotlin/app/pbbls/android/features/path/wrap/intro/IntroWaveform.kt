package app.pbbls.android.features.path.wrap.intro

import kotlin.math.roundToInt

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
        data class Segment(
            val start: Double,
            val length: Double,
            val amplitude: Int,
        )

        fun amp(intensity: Float) = (intensity * 255).roundToInt().coerceIn(1, 255)
        val pulses = IntroHaptics.events(beats).map { Segment(it.at * 1000 + leadMs, PULSE_MS.toDouble(), amp(it.tick.intensity)) }
        val steps =
            IntroHaptics.swells(beats).flatMap { swell ->
                val count = maxOf(1, (swell.sounding * 1000 / STEP_MS).toInt())
                List(count) { k ->
                    val f = if (count > 1) k / (count - 1f) else 1f
                    val start = swell.at * 1000 + leadMs + k * STEP_MS
                    Segment(start, STEP_MS.toDouble(), amp(swell.swell.from + (swell.swell.to - swell.swell.from) * f))
                }
            }
        val timings = mutableListOf<Long>()
        val amplitudes = mutableListOf<Int>()
        var cursor = 0.0
        (pulses + steps).sortedBy { it.start }.forEach { s ->
            if (s.start > cursor) {
                timings += (s.start - cursor).roundToInt().toLong()
                amplitudes += 0
                cursor = s.start
            }
            val length = s.length - (cursor - s.start)
            if (length <= 0) return@forEach
            timings += length.roundToInt().toLong()
            amplitudes += s.amplitude
            cursor += length
        }
        return IntroWave(timings, amplitudes)
    }
}

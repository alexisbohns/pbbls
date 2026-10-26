package app.pbbls.android.features.path.wrap.intro

import org.junit.Assert.assertEquals
import org.junit.Test

/** D18's waveform tier: 8 ms pulses at each tick's strength, swells as 10 ms amplitude steps. */
class IntroWaveformTest {
    @Test
    fun `ticks are pulses at their strength, with silence between`() {
        val beats = listOf(IntroBeat(IntroKey.Header, 0.0, 0.0, IntroTick.light), IntroBeat(IntroKey.Row(0), 0.05, 0.0, IntroTick.firm))
        val wave = IntroWaveform.of(beats, leadMs = 10)
        assertEquals(listOf(10L, 8L, 42L, 8L), wave.timings)
        assertEquals(listOf(0, 102, 0, 230), wave.amplitudes)
    }

    @Test
    fun `a swell ramps in 10 ms steps over its sounding time`() {
        val beats = listOf(IntroBeat(IntroKey.Gauge(0), 0.0, 0.07, swell = IntroSwell(0.2f, 0.6f, 0.3f)))
        val wave = IntroWaveform.of(beats, leadMs = 0)
        assertEquals(List(4) { 10L }, wave.timings)
        assertEquals(listOf(51, 85, 119, 153), wave.amplitudes)
    }
}

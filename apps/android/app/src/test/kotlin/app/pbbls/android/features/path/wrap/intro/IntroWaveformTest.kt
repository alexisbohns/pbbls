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
    fun `each segment's bounds round on their own, so rounding never adds up along the wave`() {
        // Pulses at 10, 50.4 and 90.8 ms: rounding each gap would land the third at 90, not 91.
        val beats =
            listOf(
                IntroBeat(IntroKey.Row(0), 0.0, 0.0, IntroTick.light),
                IntroBeat(IntroKey.Row(1), 0.0404, 0.0, IntroTick.light),
                IntroBeat(IntroKey.Row(2), 0.0808, 0.0, IntroTick.light),
            )
        assertEquals(listOf(10L, 8L, 32L, 8L, 33L, 8L), IntroWaveform.of(beats, leadMs = 10).timings)
    }

    @Test
    fun `a swell ramps in 10 ms steps over its sounding time`() {
        val beats = listOf(IntroBeat(IntroKey.Gauge(0), 0.0, 0.07, swell = IntroSwell(0.2f, 0.6f, 0.3f)))
        val wave = IntroWaveform.of(beats, leadMs = 0)
        assertEquals(List(4) { 10L }, wave.timings)
        assertEquals(listOf(51, 85, 119, 153), wave.amplitudes)
    }

    @Test
    fun `the lead delays a swell's steps as it delays a pulse`() {
        val beats = listOf(IntroBeat(IntroKey.Gauge(0), 0.0, 0.07, swell = IntroSwell(0.2f, 0.6f, 0.3f)))
        val wave = IntroWaveform.of(beats, leadMs = 40)
        assertEquals(listOf(40L) + List(4) { 10L }, wave.timings)
        assertEquals(listOf(0, 51, 85, 119, 153), wave.amplitudes)
    }

    @Test
    fun `no beats make an empty wave`() {
        assertEquals(IntroWave(emptyList(), emptyList()), IntroWaveform.of(emptyList(), leadMs = 50))
        assertEquals(IntroWave(emptyList(), emptyList()), IntroWaveform.of(listOf(IntroBeat(IntroKey.Lead, 0.0, 0.3)), leadMs = 50))
    }

    @Test
    fun `a tick of no finite strength pulses at the least amplitude`() {
        val beats = listOf(IntroBeat(IntroKey.Header, 0.0, 0.0, IntroTick(Float.NaN, 0.5f)))
        assertEquals(listOf(1), IntroWaveform.of(beats, leadMs = 0).amplitudes)
    }
}

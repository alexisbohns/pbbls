package app.pbbls.android.features.path.wrap.intro

import app.pbbls.android.features.path.wrap.intro.IntroPrimitiveKind.CLICK
import app.pbbls.android.features.path.wrap.intro.IntroPrimitiveKind.LOW_TICK
import app.pbbls.android.features.path.wrap.intro.IntroPrimitiveKind.TICK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** D18's composition, against the Pixel 8's durations (TICK 5, LOW_TICK 12, CLICK 26 ms). */
class IntroCompositionTest {
    private val pixel = mapOf(TICK to 5, LOW_TICK to 12, CLICK to 26)
    private val all = setOf(TICK, LOW_TICK, CLICK)

    @Test
    fun `a tick's primitive follows its sharpness and its scale its intensity`() {
        assertEquals(LOW_TICK, IntroComposition.kindOf(IntroTick.faint))
        assertEquals(TICK, IntroComposition.kindOf(IntroTick.light))
        assertEquals(CLICK, IntroComposition.kindOf(IntroTick.firm))
        assertEquals(LOW_TICK, IntroComposition.kindOf(IntroTick.arrive))
    }

    @Test
    fun `each pause is the beat's time less the end of the primitive before it`() {
        val beats =
            listOf(
                IntroBeat(IntroKey.Header, 0.0, 0.0, IntroTick.light),
                IntroBeat(IntroKey.Row(0), 0.032, 0.0, IntroTick.light),
                IntroBeat(IntroKey.Row(1), 0.1, 0.0, IntroTick.firm),
            )
        val prims = IntroComposition.of(beats, all, pixel, leadMs = 50)
        assertEquals(listOf(50, 27, 63), prims.map { it.pauseMs })
        assertEquals(listOf(TICK, TICK, CLICK), prims.map { it.kind })
        assertEquals(0.40f, prims[0].scale, 0f)
    }

    @Test
    fun `a missing low tick or click becomes a tick, softer or firmer`() {
        val beats = listOf(IntroBeat(IntroKey.Header, 0.0, 0.0, IntroTick.faint), IntroBeat(IntroKey.Row(0), 0.1, 0.0, IntroTick.firm))
        val prims = IntroComposition.of(beats, setOf(TICK), pixel, leadMs = 0)
        assertEquals(listOf(TICK, TICK), prims.map { it.kind })
        assertEquals(0.22f * 0.7f, prims[0].scale, 1e-6f)
        assertEquals(1f, prims[1].scale, 0f) // 0.9 × 1.2, clamped
    }

    @Test
    fun `a swell is a train of low ticks back to back, ramping, silent a floor before its closing tick`() {
        val swell = IntroSwell(0.15f, 0.75f, 0.3f)
        val beats =
            listOf(
                IntroBeat(IntroKey.Gauge(0), 0.0, 0.3, swell = swell),
                IntroBeat(IntroKey.Full(0), 0.3, 0.0, IntroTick.light),
            )
        val prims = IntroComposition.of(beats, all, pixel, leadMs = 0)
        val train = prims.dropLast(1)
        assertTrue(train.all { it.kind == LOW_TICK })
        assertEquals((275 / 12), train.size)
        assertTrue(train.drop(1).all { it.pauseMs == 0 })
        assertEquals(0.15f, train.first().scale, 1e-6f)
        assertEquals(0.75f, train.last().scale, 1e-6f)
        assertEquals(TICK, prims.last().kind)
        // The closing tick at 300 ms, after a train ending at 22 × 12 = 264 ms.
        assertEquals(36, prims.last().pauseMs)
    }
}

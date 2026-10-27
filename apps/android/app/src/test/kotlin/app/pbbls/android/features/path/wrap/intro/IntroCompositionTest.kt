package app.pbbls.android.features.path.wrap.intro

import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.wrap.CoverDay
import app.pbbls.android.core.model.wrap.CoverPebble
import app.pbbls.android.core.model.wrap.MonthSection
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
        val beats = listOf(IntroBeat(IntroKey.Leaf, 0.0, 0.0, IntroTick.knot), IntroBeat(IntroKey.Count, 0.1, 0.0, IntroTick.firm))
        val prims = IntroComposition.of(beats, all, pixel, leadMs = 0)
        assertEquals(listOf(LOW_TICK, CLICK), prims.map { it.kind })
        assertEquals(listOf(IntroTick.knot.intensity, IntroTick.firm.intensity), prims.map { it.scale })
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

    @Test
    fun `without low ticks a swell is a train of softer ticks, a step apart`() {
        val beats = listOf(IntroBeat(IntroKey.Gauge(0), 0.0, 0.3, swell = IntroSwell(0.15f, 0.75f, 0.3f)))
        val prims = IntroComposition.of(beats, setOf(TICK), pixel, leadMs = 0)
        assertEquals(22, prims.size)
        assertTrue(prims.all { it.kind == TICK })
        // Each 5 ms TICK starts 12 ms after the one before: 7 ms of silence between.
        assertTrue(prims.drop(1).all { it.pauseMs == 7 })
        assertEquals(0.15f * IntroComposition.LOW_TICK_AS_TICK, prims.first().scale, 1e-6f)
        assertEquals(0.75f * IntroComposition.LOW_TICK_AS_TICK, prims.last().scale, 1e-6f)
    }

    @Test
    fun `the lead delays a swell's train as it delays a tick`() {
        val beats = listOf(IntroBeat(IntroKey.Gauge(0), 0.0, 0.3, swell = IntroSwell(0.15f, 0.75f, 0.3f)))
        val prims = IntroComposition.of(beats, all, pixel, leadMs = 40)
        assertEquals(40, prims.first().pauseMs)
        assertTrue(prims.drop(1).all { it.pauseMs == 0 })
    }

    @Test
    fun `no beats compose no primitives`() {
        assertTrue(IntroComposition.of(emptyList(), all, pixel, leadMs = 50).isEmpty())
        // Beats that neither tick nor swell compose none either.
        assertTrue(IntroComposition.of(listOf(IntroBeat(IntroKey.Lead, 0.0, 0.3)), all, pixel, leadMs = 50).isEmpty())
        assertTrue(IntroComposition.segments(emptyList(), all, pixel, leadMs = 50).isEmpty())
    }

    /** A sequence's score at its tempo, as the clock scales it. */
    private fun List<IntroBeat>.atTempo(sequence: IntroSequence) = map { it.scaled(IntroTempo.standard.factor(sequence)) }

    @Test
    fun `the domains split after each swell into segments that start on their full ticks`() {
        val beats = IntroScore.domains(List(5) { 1.0 }, more = true).atTempo(IntroSequence.Section(MonthSection.DOMAINS))
        val whole = IntroComposition.of(beats, all, pixel, leadMs = 50)
        val segments = IntroComposition.segments(beats, all, pixel, leadMs = 50)
        assertEquals(1 + 5, segments.size)
        // The first segment is the composition's head, its lead included: the card's arrival at 0.
        assertEquals(0.0, segments.first().atMs, 0.0)
        assertEquals(whole.take(segments.first().primitives.size), segments.first().primitives)
        segments.drop(1).forEachIndexed { row, segment ->
            val full = beats.first { it.key == IntroKey.Full(row) }
            assertEquals(full.at * 1000, segment.atMs, 1.0)
            assertEquals(IntroPrimitive(TICK, IntroTick.light.intensity, 0), segment.primitives.first())
        }
        // Every segment fits the HAL's 254 primitives, so none is chunked.
        assertTrue(segments.all { it.primitives.size <= 254 })
    }

    @Test
    fun `the domains' segments hold the whole composition, only their first pauses re-anchored`() {
        val beats = IntroScore.domains(List(5) { 1.0 }, more = true).atTempo(IntroSequence.Section(MonthSection.DOMAINS))
        val whole = IntroComposition.of(beats, all, pixel, leadMs = 50)
        val segments = IntroComposition.segments(beats, all, pixel, leadMs = 50)
        val firsts =
            segments
                .runningFold(0) { at, segment -> at + segment.primitives.size }
                .drop(1)
                .dropLast(1)
                .toSet()
        val reanchored = whole.mapIndexed { i, p -> if (i in firsts) p.copy(pauseMs = 0) else p }
        assertEquals(reanchored, segments.flatMap { it.primitives })
        // Without low ticks, the softer ticks' trains split the same way.
        val ticks = IntroComposition.segments(beats, setOf(TICK), pixel, leadMs = 50)
        assertEquals(1 + 5, ticks.size)
        assertEquals(IntroComposition.of(beats, setOf(TICK), pixel, leadMs = 50).size, ticks.sumOf { it.primitives.size })
    }

    @Test
    fun `a score with no swell is one segment, the whole composition`() {
        val pebble = CoverPebble("m", null, 2, ValencePolarity.HIGHLIGHT)
        val days = listOf(CoverDay(0, listOf(pebble)), CoverDay(4, listOf(pebble, pebble)))
        val beats = IntroScore.cover(days, 31, listOf(0, 4, 30), 2, emptyList()).atTempo(IntroSequence.Cover)
        val segments = IntroComposition.segments(beats, all, pixel, leadMs = 50)
        assertEquals(1, segments.size)
        assertEquals(IntroComposition.of(beats, all, pixel, leadMs = 50), segments.single().primitives)
        assertEquals(IntroHaptics.events(beats).first().at * 1000, segments.single().atMs, 0.0)
    }

    @Test
    fun `a swell straight after another starts its own segment, and a closing swell none after it`() {
        val swell = IntroSwell(0.15f, 0.75f, 0.3f)
        val beats =
            listOf(
                IntroBeat(IntroKey.Gauge(0), 0.0, 0.3, swell = swell),
                IntroBeat(IntroKey.Gauge(1), 0.3, 0.3, swell = swell),
            )
        val segments = IntroComposition.segments(beats, all, pixel, leadMs = 0)
        assertEquals(2, segments.size)
        assertEquals(0.0, segments[0].atMs, 0.0)
        assertEquals(300.0, segments[1].atMs, 1e-9)
        assertEquals(listOf(22, 22), segments.map { it.primitives.size })
        assertEquals(0, segments[1].primitives.first().pauseMs)
    }
}

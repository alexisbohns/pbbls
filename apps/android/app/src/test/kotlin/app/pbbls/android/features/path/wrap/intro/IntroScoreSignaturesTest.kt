package app.pbbls.android.features.path.wrap.intro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `IntroScoreSignaturesTests` (#1241 D10): each card's signature, one row at a time, each in its parts. */
class IntroScoreSignaturesTest {
    private fun List<IntroBeat>.beat(key: IntroKey): IntroBeat = requireNotNull(firstOrNull { it.key == key }) { "no beat for $key" }

    @Test
    fun `a week's column - its date, its days down the column, a tick per pebble, none for a slot, then its cairn`() {
        val beats =
            IntroScore.weekColumns(
                listOf(
                    IntroWeekColumn(listOf(null, 0, 2, null, null, null, null), stones = 2),
                    IntroWeekColumn(listOf(0, 0, 0, 0, 0, 0, 1), stones = 1),
                ),
            )
        assertEquals(IntroTick.arrive, beats.beat(IntroKey.Header).tick)
        val date = beats.beat(IntroKey.Words(0))
        assertEquals(IntroScore.ROWS_AT, date.at, 0.0)
        assertEquals(IntroTick.light, date.tick)
        // A day outside the month has no beat; an empty day lands silently.
        assertFalse(beats.any { it.key == IntroKey.Rest(0, 0) })
        val rest = beats.beat(IntroKey.Rest(0, 1))
        assertEquals(IntroScore.PART_GAP, rest.at - date.at, 1e-12)
        assertNull(rest.tick)
        val first = beats.beat(IntroKey.Pip(0, 2, 0))
        val second = beats.beat(IntroKey.Pip(0, 2, 1))
        assertEquals(IntroScore.DAY_GAP, first.at - rest.at, 1e-12)
        assertEquals(IntroScore.PIP_GAP, second.at - first.at, 1e-12)
        assertEquals(IntroTick.light, first.tick)
        assertEquals(IntroTick.light, second.tick)
        val stone = beats.beat(IntroKey.Cairn(0, 0))
        assertEquals(IntroScore.CAIRN_AFTER, stone.at - second.at, 1e-12)
        assertEquals(IntroTick.cairn(1), beats.beat(IntroKey.Cairn(0, 1)).tick)
        assertEquals(IntroScore.NEXT_WEEK, beats.beat(IntroKey.Words(1)).at - beats.beat(IntroKey.Cairn(0, 1)).at, 1e-12)
    }

    @Test
    fun `an empty week's column ends on its dotted stone, on a faint tick`() {
        val beats = IntroScore.weekColumns(listOf(IntroWeekColumn(List(7) { 0 }, stones = 0)))
        assertEquals(IntroTick.faint, beats.beat(IntroKey.Cairn(0, 0)).tick)
        assertFalse(beats.any { it.key is IntroKey.Pip })
    }

    @Test
    fun `a milestone stamps, ticking at impact as firmly as it is intense, then its date, name and souls`() {
        val beats =
            IntroScore.milestones(
                listOf(IntroMilestoneShape(intensity = 3, souls = 1), IntroMilestoneShape(intensity = 1, souls = 0)),
            )
        val stamp = beats.beat(IntroKey.Stamp(0))
        assertEquals(IntroScore.ROWS_AT, stamp.at, 0.0)
        assertEquals(IntroScore.STAMP_SPAN, stamp.duration, 0.0)
        assertNull(stamp.tick)
        val impact = beats.beat(IntroKey.Impact(0))
        assertEquals(IntroMotion.STAMP_IMPACT * IntroScore.STAMP_SPAN, impact.at - stamp.at, 1e-12)
        assertEquals(IntroTick.seal(3), impact.tick)
        val date = beats.beat(IntroKey.Date(0))
        val name = beats.beat(IntroKey.Name(0))
        assertEquals(IntroScore.STAMP_SPAN, date.at - stamp.at, 1e-12)
        assertEquals(IntroScore.PART_GAP, name.at - date.at, 1e-12)
        val soul = beats.beat(IntroKey.Mark(0, 0))
        assertEquals(IntroScore.PART_GAP, soul.at - name.at, 1e-12)
        assertEquals(IntroScore.NEXT_MILESTONE, beats.beat(IntroKey.Stamp(1)).at - soul.at, 1e-12)
        assertEquals(IntroTick.seal(1), beats.beat(IntroKey.Impact(1)).tick)
    }

    @Test
    fun `a domain - its name, then its gauge swelling for longer the fuller it is, a tick as it stops`() {
        val beats = IntroScore.domains(listOf(1.0, 0.5), more = true)
        val row = beats.beat(IntroKey.Row(0))
        assertEquals(IntroScore.ROWS_AT, row.at, 0.0)
        assertEquals(IntroTick.light, row.tick)
        val gauge = beats.beat(IntroKey.Gauge(0))
        assertEquals(IntroScore.PART_GAP, gauge.at - row.at, 1e-12)
        assertEquals(IntroScore.fillSpan(1.0), gauge.duration, 0.0)
        assertNull(gauge.tick)
        assertEquals(IntroSwell(IntroScore.SWELL_FROM, IntroScore.swellTo(1.0), IntroScore.SWELL_SHARPNESS), gauge.swell)
        val full = beats.beat(IntroKey.Full(0))
        assertEquals(gauge.duration, full.at - gauge.at, 1e-12)
        assertEquals(IntroTick.light, full.tick)
        assertEquals(IntroScore.NEXT_ROW, beats.beat(IntroKey.Row(1)).at - full.at, 1e-12)
        assertTrue(IntroScore.fillSpan(0.5) < IntroScore.fillSpan(1.0))
        assertTrue(IntroScore.swellTo(0.5) < IntroScore.swellTo(1.0))
        assertEquals(beats.beat(IntroKey.Full(1)).at, beats.beat(IntroKey.More).at, 0.0)
        assertFalse(IntroScore.domains(listOf(1.0), more = false).any { it.key == IntroKey.More })
    }
}

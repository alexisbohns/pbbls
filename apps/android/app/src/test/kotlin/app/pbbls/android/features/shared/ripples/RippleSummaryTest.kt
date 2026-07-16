package app.pbbls.android.features.shared.ripples

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [RippleSummary] decoding + the client-side level-progress math — mirrors iOS
 * `RippleSummaryDecodingTests.swift`. Thresholds [1,5,9,13,17,21] track the
 * `v_ripple` CASE buckets (20260516000001).
 */
class RippleSummaryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes the v_ripple row shape`() {
        val summary =
            json.decodeFromString<RippleSummary>(
                """{ "ripple_level": 2, "pebbles_28d": 7, "active_today": false }""",
            )
        assertEquals(2, summary.rippleLevel)
        assertEquals(7, summary.pebbles28d)
        assertFalse(summary.activeToday)
    }

    @Test
    fun `level zero needs one pebble to enter level one`() {
        val summary = RippleSummary(rippleLevel = 0, pebbles28d = 0, activeToday = false)
        assertEquals(1, summary.nextLevel)
        assertEquals(1, summary.pebblesToNextLevel)
    }

    @Test
    fun `mid level counts pebbles remaining to the next threshold`() {
        val summary = RippleSummary(rippleLevel = 1, pebbles28d = 3, activeToday = true)
        assertEquals(2, summary.nextLevel)
        // Level 2 enters at 5 pebbles; 5 - 3 = 2 to go.
        assertEquals(2, summary.pebblesToNextLevel)
    }

    @Test
    fun `an overshoot clamps to zero remaining`() {
        val summary = RippleSummary(rippleLevel = 4, pebbles28d = 20, activeToday = true)
        assertEquals(5, summary.nextLevel)
        // Level 5 enters at 17; already past it.
        assertEquals(0, summary.pebblesToNextLevel)
    }

    @Test
    fun `level six is terminal`() {
        val summary = RippleSummary(rippleLevel = 6, pebbles28d = 28, activeToday = true)
        assertNull(summary.nextLevel)
        assertNull(summary.pebblesToNextLevel)
    }
}

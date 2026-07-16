package app.pbbls.android.features.shared.ripples

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [rippleStrokeTone] truth table from issue #442, verbatim — mirrors iOS
 * `RippleStrokeColorTests.swift`:
 *   - strokeId > level                  → DEFAULT
 *   - strokeId <= level &&  activeToday → ACTIVE
 *   - strokeId <= level && !activeToday → INACTIVE
 */
class RippleStrokeColorTest {
    @Test
    fun `strokes outside the level are DEFAULT regardless of activity`() {
        for (active in listOf(true, false)) {
            assertEquals(RippleStrokeTone.DEFAULT, rippleStrokeTone(strokeId = 1, level = 0, activeToday = active))
            assertEquals(RippleStrokeTone.DEFAULT, rippleStrokeTone(strokeId = 4, level = 3, activeToday = active))
            assertEquals(RippleStrokeTone.DEFAULT, rippleStrokeTone(strokeId = 6, level = 5, activeToday = active))
        }
    }

    @Test
    fun `strokes within the level are ACTIVE when a pebble was created today`() {
        assertEquals(RippleStrokeTone.ACTIVE, rippleStrokeTone(strokeId = 1, level = 1, activeToday = true))
        assertEquals(RippleStrokeTone.ACTIVE, rippleStrokeTone(strokeId = 3, level = 3, activeToday = true))
        assertEquals(RippleStrokeTone.ACTIVE, rippleStrokeTone(strokeId = 1, level = 6, activeToday = true))
        assertEquals(RippleStrokeTone.ACTIVE, rippleStrokeTone(strokeId = 6, level = 6, activeToday = true))
    }

    @Test
    fun `strokes within the level are INACTIVE when no pebble was created today`() {
        assertEquals(RippleStrokeTone.INACTIVE, rippleStrokeTone(strokeId = 1, level = 1, activeToday = false))
        assertEquals(RippleStrokeTone.INACTIVE, rippleStrokeTone(strokeId = 3, level = 3, activeToday = false))
        assertEquals(RippleStrokeTone.INACTIVE, rippleStrokeTone(strokeId = 6, level = 6, activeToday = false))
    }

    @Test
    fun `the full 6x7x2 matrix follows the rule`() {
        for (strokeId in 1..6) {
            for (level in 0..6) {
                for (active in listOf(true, false)) {
                    val expected =
                        when {
                            strokeId > level -> RippleStrokeTone.DEFAULT
                            active -> RippleStrokeTone.ACTIVE
                            else -> RippleStrokeTone.INACTIVE
                        }
                    assertEquals(
                        "strokeId=$strokeId level=$level active=$active",
                        expected,
                        rippleStrokeTone(strokeId, level, active),
                    )
                }
            }
        }
    }
}

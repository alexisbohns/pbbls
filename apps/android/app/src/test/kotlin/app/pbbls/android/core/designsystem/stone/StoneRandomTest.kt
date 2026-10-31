package app.pbbls.android.core.designsystem.stone

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The exploration harness's generator, bit for bit. The draws are the iOS
 * test's vectors (`PebblesTests/HomeShortcutsTests.swift`, `randomMatchesTheHarness`),
 * themselves taken from node: the seeds are part of the cross-surface contract,
 * so the same stone draws on every surface.
 */
class StoneRandomTest {
    @Test
    fun `the quick actions row seed draws the harness values`() {
        val random = StoneRandom("camera|fixed|rock|q11")
        for (expected in listOf(0.9302541588, 0.5075708053, 0.6537410403)) {
            assertEquals(expected, random.next(), 1e-9)
        }
    }

    @Test
    fun `the bento seed draws the harness values`() {
        val random = StoneRandom("quick|fixed|q12split")
        for (expected in listOf(0.0461277429, 0.2574864214, 0.5876142546)) {
            assertEquals(expected, random.next(), 1e-9)
        }
    }

    @Test
    fun `one seed draws one sequence`() {
        val a = StoneRandom("first-five")
        val b = StoneRandom("first-five")
        repeat(100) { assertEquals(a.next(), b.next(), 0.0) }
    }
}

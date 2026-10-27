package app.pbbls.android.features.path.wrap.intro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `IntroMotionTests.curve` and `IntroScoreSignaturesTests.stampCurves`. */
class IntroMotionTest {
    @Test
    fun `a run's beats go slow, fast, slow, from 0 to its duration`() {
        assertTrue(IntroCurve.times(0, 1.5).isEmpty())
        assertEquals(listOf(0.0), IntroCurve.times(1, 1.5))
        val times = IntroCurve.times(31, 1.5)
        assertEquals(31, times.size)
        assertEquals(0.0, times.first(), 0.0)
        assertEquals(1.5, times.last(), 1e-12)
        val gaps = times.zipWithNext { a, b -> b - a }
        assertTrue(gaps.all { it > 0 })
        val middle = gaps[gaps.size / 2]
        assertTrue(middle < gaps.first() && middle < gaps.last())
        // The fastest stretch still clears the haptics' 25 ms floor.
        assertTrue(gaps.min() > 0.025)
    }

    @Test
    fun `a rising part starts a whole rise below its place and eases up, exactly there once landed`() {
        assertEquals(1.0, IntroMotion.riseLift(0.0), 0.0)
        // Cubic ease-out: half way in, an eighth of the rise is left.
        assertEquals(0.125, IntroMotion.riseLift(0.5), 1e-12)
        assertEquals(0.0, IntroMotion.riseLift(1.0), 0.0)
        assertEquals(0.0, IntroMotion.riseLift(1.5), 0.0)
    }

    @Test
    fun `a cairn's stones tick a little firmer each, capped at 0_8`() {
        assertEquals(0.32f, IntroTick.cairn(0).intensity, 1e-6f)
        assertEquals(0.74f, IntroTick.cairn(6).intensity, 1e-6f)
        assertEquals(0.8f, IntroTick.cairn(20).intensity, 0f)
    }

    @Test
    fun `a stamp comes in large and clear, presses past its size at impact, and settles`() {
        assertEquals(1.25, IntroMotion.stampScale(0.0), 0.0)
        assertTrue(IntroMotion.stampScale(IntroMotion.STAMP_IMPACT) < 1)
        assertEquals(1.0, IntroMotion.stampScale(1.0), 1e-12)
        assertEquals(0.0, IntroMotion.stampOpacity(0.0), 0.0)
        assertEquals(1.0, IntroMotion.stampOpacity(IntroMotion.STAMP_IMPACT), 0.0)
    }
}

package app.pbbls.android.features.path.card.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class RingFitTest {
    /** A monospaced fake: every character advances 0.6 em. */
    private val measure: (String, Float) -> Float = { text, sizeSp -> text.length * sizeSp * 0.6f }

    private fun seamError(
        fit: RingFit,
        circumference: Float,
    ): Float = kotlin.math.abs(measure(fit.text, fit.sizeSp) + fit.trackingPx * fit.text.length - circumference)

    @Test
    fun `a short name repeats as often as the circle allows, closing exactly`() {
        val fit = RingFits.fit("Santé", circumferencePx = 180f, locale = Locale.FRENCH, measure = measure)
        assertEquals(4, fit.repetitions)
        assertEquals(8.5f, fit.sizeSp)
        assertEquals("SANTÉ · ".repeat(4), fit.text)
        assertTrue(seamError(fit, 180f) < 0.5f)
    }

    @Test
    fun `a long name steps the size down before it falls under two repetitions`() {
        val fit = RingFits.fit("Current events", circumferencePx = 170f, locale = Locale.ENGLISH, measure = measure)
        assertEquals(2, fit.repetitions)
        assertEquals(8f, fit.sizeSp)
        assertTrue(seamError(fit, 170f) < 0.5f)
    }

    @Test
    fun `a name that cannot ring twice even at the smallest size is set once`() {
        val fit = RingFits.fit("Spirituality", circumferencePx = 60f, locale = Locale.ENGLISH, measure = measure)
        assertEquals(1, fit.repetitions)
        assertEquals(7.5f, fit.sizeSp)
        assertEquals("SPIRITUALITY · ", fit.text)
        assertEquals(0f, fit.trackingPx)
    }

    @Test
    fun `tracking is never negative`() {
        for (c in listOf(90f, 120f, 163.2f, 200f, 260f)) {
            val fit = RingFits.fit("Friends", circumferencePx = c, locale = Locale.ENGLISH, measure = measure)
            assertTrue("tracking at $c", fit.trackingPx >= 0f)
        }
    }

    @Test
    fun `the name is set in the locale's capitals`() {
        val fit = RingFits.fit("Bien-être", circumferencePx = 200f, locale = Locale.FRENCH, measure = measure)
        assertTrue(fit.text.startsWith("BIEN-ÊTRE · "))
    }
}

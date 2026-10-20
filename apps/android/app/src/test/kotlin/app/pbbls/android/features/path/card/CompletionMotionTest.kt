package app.pbbls.android.features.path.card

import org.junit.Assert.assertEquals
import org.junit.Test

/** The print's one timeline (pebble card spec §Completion) through its three phases and the parts' stagger. */
class CompletionMotionTest {
    private fun assertProgress(
        expected: CompletionProgress,
        at: Float,
    ) {
        val actual = CompletionMotion.at(at)
        assertEquals("print at $at", expected.print, actual.print, EPS)
        assertEquals("stone at $at", expected.stone, actual.stone, EPS)
        assertEquals("rise at $at", expected.rise, actual.rise, EPS)
    }

    @Test
    fun `the phases run over 0 to 0_35, 0_25 to 0_6 and 0_6 to 1`() {
        assertProgress(CompletionProgress(0f, 0f, 0f), at = 0f)
        assertProgress(CompletionProgress(0.2f / 0.35f, 0f, 0f), at = 0.2f)
        assertProgress(CompletionProgress(1f, 0.1f / 0.35f, 0f), at = 0.35f)
        assertProgress(CompletionProgress(1f, 1f, 0f), at = 0.6f)
        assertProgress(CompletionProgress(1f, 1f, 0.5f), at = 0.8f)
        assertProgress(CompletionProgress.REST, at = 1f)
    }

    @Test
    fun `the dashed stone lands, and the lit stone is in, at the press`() {
        val stone = CompletionMotion.at(CompletionMotion.PRESS_AT).stone
        assertEquals(CompletionMotion.LANDING, stone, EPS)
        assertEquals(1f, CompletionMotion.litStoneAlpha(stone), EPS)
        assertEquals(0f, CompletionMotion.litStoneAlpha(CompletionMotion.at(0.3f).stone), EPS)
    }

    @Test
    fun `the parts print in turn, and all are printed when the print is`() {
        val halfway = 0.5f
        assertEquals(1f, PrintPart.CARD.alpha(halfway), EPS)
        val order = listOf(PrintPart.LOCKUP, PrintPart.POSTMARKS, PrintPart.BAND, PrintPart.FOOTER).map { it.alpha(halfway) }
        assertEquals(order.sortedDescending(), order)
        assertEquals(0f, PrintPart.FOOTER.alpha(halfway), EPS)
        PrintPart.entries.forEach { assertEquals(it.name, 1f, it.alpha(1f), EPS) }
        PrintPart.entries.forEach { assertEquals(it.name, 0f, it.alpha(0f), EPS) }
    }

    private companion object {
        const val EPS = 1e-4f
    }
}

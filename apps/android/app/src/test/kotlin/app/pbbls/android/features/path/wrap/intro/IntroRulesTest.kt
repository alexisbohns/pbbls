package app.pbbls.android.features.path.wrap.intro

import app.pbbls.android.core.model.wrap.MonthSection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** iOS `IntroRulesTests` (#1241 D3, D11). */
class IntroRulesTest {
    @Test
    fun `no intro under reduce motion, while touch exploration runs, or at a large text size`() {
        assertTrue(IntroPolicy.plays(reduceMotion = false, touchExploration = false, largeText = false))
        assertFalse(IntroPolicy.plays(reduceMotion = true, touchExploration = false, largeText = false))
        assertFalse(IntroPolicy.plays(reduceMotion = false, touchExploration = true, largeText = false))
        assertFalse(IntroPolicy.plays(reduceMotion = false, touchExploration = false, largeText = true))
        assertTrue(IntroPolicy.isLargeText(1.5f))
        assertFalse(IntroPolicy.isLargeText(1.3f))
    }

    @Test
    fun `the ledger holds a month's sequence once, a reset forgets that month only`() {
        val ledger = IntroLedger()
        val october = YearMonth.of(2026, 10)
        val september = YearMonth.of(2026, 9)
        val gem = IntroSequence.Section(MonthSection.GEM)
        ledger.record(october, IntroSequence.Cover)
        ledger.record(september, gem)
        assertTrue(ledger.contains(october, IntroSequence.Cover))
        assertFalse(ledger.contains(october, gem))
        ledger.reset(october)
        assertFalse(ledger.contains(october, IntroSequence.Cover))
        assertTrue(ledger.contains(september, gem))
    }
}

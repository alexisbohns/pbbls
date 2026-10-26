package app.pbbls.android.features.path.wrap.month

import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.features.path.wrap.intro.IntroSequence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** iOS `MonthDeckTests.topIsIdentity`, `.comingIsHeld`, `.leavingLifts`, `.edges`, `.step` and `.noStep`. */
class MonthDeckMotionTest {
    @Test
    fun `the top card is drawn as it is, so its settled render is the page's`() {
        assertEquals(1f, MonthDeckMotion.scale(0f), 0f)
        assertEquals(0f, MonthDeckMotion.turn(0f), 0f)
        assertEquals(0f, MonthDeckMotion.offset(0f, page = 800f, peek = MonthDeckMotion.PEEK), 0f)
    }

    @Test
    fun `a card to come is held in place beneath the top one, a little smaller, its foot peeking`() {
        // One page down, it is drawn a foot below the top: its layout (800) plus the offset.
        assertEquals(MonthDeckMotion.PEEK, 800f + MonthDeckMotion.offset(1f, page = 800f, peek = MonthDeckMotion.PEEK), 1e-4f)
        // Half way up, it has risen half the foot, not half the page.
        assertEquals(MonthDeckMotion.PEEK / 2, 400f + MonthDeckMotion.offset(0.5f, page = 800f, peek = MonthDeckMotion.PEEK), 1e-4f)
        assertEquals(1f - MonthDeckMotion.SHRINK, MonthDeckMotion.scale(1f), 1e-6f)
    }

    @Test
    fun `a leaving card lifts and turns as it goes, and is not held`() {
        assertEquals(1f + MonthDeckMotion.LIFT, MonthDeckMotion.scale(-1f), 1e-6f)
        assertEquals(-MonthDeckMotion.MAX_TURN, MonthDeckMotion.turn(-1f), 0f)
        assertEquals(0f, MonthDeckMotion.offset(-0.5f, page = 800f, peek = MonthDeckMotion.PEEK), 0f)
    }

    @Test
    fun `the deck's static edges stand for the cards past the next, two at most`() {
        assertEquals(listOf(0, 0, 1, 2, 2), listOf(0, 1, 2, 3, 7).map(MonthDeckMotion::edges))
    }

    @Test
    fun `each card knows its place in the deck, the segments filled up to it`() {
        val cards = MonthDeck.cards(SpineFixtureWeek.monthWrap, SpineFixtureWeek.lookups)
        assertEquals(MonthDeckStep(0, 9), MonthDeckStep.of(IntroSequence.Cover, cards))
        val gem = MonthDeckStep.of(IntroSequence.Section(MonthSection.GEM), cards)!!
        assertEquals(4, gem.index)
        assertEquals(listOf(true, true, true, true, true, false, false, false, false), (0 until gem.count).map(gem::isFilled))
        assertEquals(8, MonthDeckStep.of(IntroSequence.End, cards)?.index)
    }

    @Test
    fun `a deck of one has no stepper, and a card it does not deal has no place`() {
        assertNull(MonthDeckStep.of(IntroSequence.Cover, listOf(IntroSequence.Cover)))
        assertNull(
            MonthDeckStep.of(
                IntroSequence.Section(MonthSection.GEM),
                listOf(IntroSequence.Cover, IntroSequence.Section(MonthSection.WEEKS)),
            ),
        )
    }
}

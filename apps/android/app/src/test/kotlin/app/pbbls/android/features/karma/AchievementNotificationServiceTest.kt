package app.pbbls.android.features.karma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The unlock moment's queue (D13): an empty response never presents, cards
 * advance one at a time, the last card ends the moment, and dismissing skips
 * the rest of the queue. No virtual clock here — unlike the karma pastille the
 * moment has no auto-dismiss; the user taps through it.
 */
class AchievementNotificationServiceTest {
    private fun card(
        slug: String,
        karma: Int = 0,
    ) = AchievementMomentCard(slug = slug, record = null, karmaGranted = karma)

    @Test
    fun `an empty result set never presents`() {
        val service = AchievementNotificationService()

        service.present(emptyList())
        assertNull(service.currentCard)
        assertTrue(service.cards.isEmpty())
    }

    @Test
    fun `a single unlock shows one card and ends on advance`() {
        val service = AchievementNotificationService()

        service.present(listOf(card("first-soul", karma = 5)))
        assertEquals("first-soul", service.currentCard?.slug)
        assertEquals(5, service.currentCard?.karmaGranted)
        assertTrue(service.isShowingLastCard)

        service.advance()
        assertNull(service.currentCard)
    }

    @Test
    fun `several unlocks chain in the order they were given`() {
        val service = AchievementNotificationService()

        service.present(listOf(card("pebble-count-1"), card("pebble-count-10"), card("first-glyph")))
        assertEquals("pebble-count-1", service.currentCard?.slug)
        assertTrue(!service.isShowingLastCard)

        service.advance()
        assertEquals("pebble-count-10", service.currentCard?.slug)

        service.advance()
        assertEquals("first-glyph", service.currentCard?.slug)
        assertTrue(service.isShowingLastCard)

        service.advance()
        assertNull(service.currentCard)
    }

    @Test
    fun `dismissing skips the rest of the queue`() {
        val service = AchievementNotificationService()

        service.present(listOf(card("a"), card("b"), card("c")))
        service.dismiss()

        assertNull(service.currentCard)
        assertTrue(service.cards.isEmpty())
    }

    @Test
    fun `a fresh moment replaces the previous queue`() {
        val service = AchievementNotificationService()

        service.present(listOf(card("a"), card("b")))
        service.advance()
        assertEquals("b", service.currentCard?.slug)

        service.present(listOf(card("c")))
        assertEquals("c", service.currentCard?.slug)
        assertEquals(1, service.cards.size)
        assertTrue(service.isShowingLastCard)
    }
}

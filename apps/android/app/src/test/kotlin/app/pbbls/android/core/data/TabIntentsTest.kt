package app.pbbls.android.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Cross-tab intents (#1383, spec D10): posted by one tab, taken exactly once by the target. */
class TabIntentsTest {
    private val week = LocalDate.of(2026, 10, 5)

    @Test
    fun `a posted intent is taken once`() {
        val intents = TabIntents()
        intents.post(TabIntent.FocusWeek(week))
        assertEquals(TabIntent.FocusWeek(week), intents.take(TabIntent.FocusWeek::class))
        assertNull("taken already", intents.take(TabIntent.FocusWeek::class))
        assertNull(intents.pending.value)
    }

    @Test
    fun `a take of another type leaves the intent for its own target`() {
        val intents = TabIntents()
        intents.post(TabIntent.CreateSoul)
        assertNull(intents.take(TabIntent.FocusWeek::class))
        assertNull(intents.take(TabIntent.CreateCollection::class))
        assertEquals(TabIntent.CreateSoul, intents.take(TabIntent.CreateSoul::class))
    }

    @Test
    fun `a newer post replaces one nobody took`() {
        val intents = TabIntents()
        intents.post(TabIntent.CarveGlyph)
        intents.post(TabIntent.FocusWeek(week))
        assertNull(intents.take(TabIntent.CarveGlyph::class))
        assertEquals(week, intents.take<TabIntent.FocusWeek>()?.weekStart)
    }

    @Test
    fun `nothing posted, nothing taken`() {
        assertNull(TabIntents().take(TabIntent.CreateCollection::class))
    }
}

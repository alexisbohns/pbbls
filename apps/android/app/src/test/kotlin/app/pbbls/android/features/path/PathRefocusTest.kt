package app.pbbls.android.features.path

import app.pbbls.android.features.path.models.WeekRollEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PathRefocusTest {
    private fun entry(weekStart: LocalDate) = WeekRollEntry(weekStart, emptyList())

    private val june1 = LocalDate.of(2026, 6, 1)
    private val june22 = LocalDate.of(2026, 6, 22)
    private val july6 = LocalDate.of(2026, 7, 6)
    private val today = LocalDate.of(2026, 7, 11)

    @Test
    fun `keeps an existing focus`() {
        val entries = listOf(entry(june1), entry(july6))
        assertEquals(june1, refocusedWeekStart(entries, june1, today))
    }

    @Test
    fun `prefers the current week when focus is unset or vanished`() {
        val entries = listOf(entry(june1), entry(july6))
        assertEquals(july6, refocusedWeekStart(entries, null, today))
        assertEquals(july6, refocusedWeekStart(entries, june22, today))
    }

    @Test
    fun `falls back to the closest entry when the current week is absent`() {
        // Focus on a vanished week; no current-week entry in the roll.
        val entries = listOf(entry(june1), entry(june22))
        assertEquals(june22, refocusedWeekStart(entries, LocalDate.of(2026, 6, 29), today))
    }

    @Test
    fun `null only for an empty roll`() {
        assertNull(refocusedWeekStart(emptyList(), null, today))
    }
}

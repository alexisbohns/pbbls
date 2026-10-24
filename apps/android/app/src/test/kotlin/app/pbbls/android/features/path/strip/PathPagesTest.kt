package app.pbbls.android.features.path.strip

import app.pbbls.android.core.model.WeekRollEntry
import app.pbbls.android.core.model.wrap.DayWeight
import app.pbbls.android.core.model.wrap.Strip
import app.pbbls.android.core.model.wrap.StripItem
import app.pbbls.android.testing.UiFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PathPagesTest {
    private val june1 = LocalDate.of(2026, 6, 1)
    private val june22 = LocalDate.of(2026, 6, 22)
    private val june29 = LocalDate.of(2026, 6, 29)
    private val july6 = LocalDate.of(2026, 7, 6)
    private val today = LocalDate.of(2026, 7, 11)
    private val june = YearMonth.of(2026, 6)

    private fun entry(
        weekStart: LocalDate,
        moments: Int = 0,
    ) = WeekRollEntry(weekStart, List(moments) { UiFixtures.pathMoment(id = "$weekStart-$it") })

    private fun week(
        weekStart: LocalDate,
        focusable: Boolean = true,
        live: Boolean = false,
    ) = StripItem.Week(
        weekStart,
        0,
        2026,
        if (focusable) 1 else 0,
        focusable,
        live,
        List(7) {
            DayWeight(
                if (it == 0 &&
                    focusable
                ) {
                    1
                } else {
                    0
                },
                0,
            )
        },
    )

    // MARK: - Items

    @Test
    fun `the server's strip is kept as sent, months and spacers included`() {
        val strip =
            Strip(listOf(week(june1), week(june22, focusable = false), week(june29), StripItem.Month(june, 4), week(july6, live = true)))
        val items = PathPages.items(strip, listOf(entry(june1, 1), entry(june29, 1), entry(july6)), today)
        assertEquals(strip.items, items)
        assertEquals(
            listOf(PathPage.Week(june1), PathPage.Week(june29), PathPage.Month(june), PathPage.Week(july6)),
            PathPages.pages(items),
        )
    }

    @Test
    fun `without a strip the feed's weeks stand in, with blank cairns`() {
        val items = PathPages.items(null, listOf(entry(june1, 2), entry(july6)), today)
        assertEquals(listOf(PathPage.Week(june1), PathPage.Week(july6)), PathPages.pages(items))
        val first = items.first() as StripItem.Week
        assertEquals(2, first.moments)
        assertTrue(first.days.isEmpty())
        assertEquals(23, first.isoWeek)
        assertTrue((items.last() as StripItem.Week).live)
    }

    @Test
    fun `a week the strip has not seen yet joins in date order, before the month it precedes`() {
        // The strip lags a write: June 29 holds a moment now, and June closes in that week.
        val strip = Strip(listOf(week(june1), week(june22, focusable = false), StripItem.Month(june, 4), week(july6, live = true)))
        val items = PathPages.items(strip, listOf(entry(june1, 1), entry(june22, 1), entry(june29, 1), entry(july6)), today)
        assertEquals(
            listOf(PathPage.Week(june1), PathPage.Week(june22), PathPage.Week(june29), PathPage.Month(june), PathPage.Week(july6)),
            PathPages.pages(items),
        )
        assertEquals("the spacer is replaced in place", 5, items.size)
    }

    // MARK: - Refocus (the iOS `PathView.load()` rule)

    private val pages = listOf(PathPage.Week(june1), PathPage.Month(june), PathPage.Week(july6))

    @Test
    fun `keeps an existing focus, a month included`() {
        assertEquals(PathPage.Week(june1), PathPages.refocused(pages, PathPage.Week(june1), today))
        assertEquals(PathPage.Month(june), PathPages.refocused(pages, PathPage.Month(june), today))
    }

    @Test
    fun `prefers the current week when focus is unset or vanished`() {
        assertEquals(PathPage.Week(july6), PathPages.refocused(pages, null, today))
        assertEquals(PathPage.Week(july6), PathPages.refocused(pages, PathPage.Week(june22), today))
    }

    @Test
    fun `falls back to the closest page when the current week is absent, a month by its last day`() {
        val past = listOf(PathPage.Week(june1), PathPage.Month(june))
        assertEquals(PathPage.Month(june), PathPages.refocused(past, PathPage.Week(june29), today))
        assertEquals(PathPage.Week(june1), PathPages.refocused(past, PathPage.Week(LocalDate.of(2026, 6, 8)), today))
    }

    @Test
    fun `null only without pages`() {
        assertNull(PathPages.refocused(emptyList(), null, today))
    }
}

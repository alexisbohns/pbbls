package app.pbbls.android.features.path.wrap.month

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.features.path.wrap.copy.WrapCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** iOS `MonthDeckTests.titles` and `MonthCardsTests.everyWeek`'s head. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en")
class MonthHeadTest {
    private val copy = WrapCopy(ApplicationProvider.getApplicationContext<Application>().resources, Locale.ENGLISH)
    private val wrap = SpineFixtureWeek.monthWrap
    private val lookups = SpineFixtureWeek.lookups

    @Test
    fun `every section of the fixture shows, and the weeks head counts every week`() {
        MonthSection.entries.forEach { assertTrue("$it", MonthSections.isShown(it, wrap, lookups)) }
        assertEquals(
            copy.weeksHeader(wrap.weeks!!.size),
            MonthSections.head(MonthSection.WEEKS, wrap, lookups, copy, DayView.CIRCLE).label,
        )
    }

    @Test
    fun `the persons hint reads clockwise only around a ring`() {
        assertEquals(copy.clockwiseHint(), MonthSections.head(MonthSection.PERSONS, wrap, lookups, copy, DayView.CIRCLE).hint)
        assertEquals(null, MonthSections.head(MonthSection.PERSONS, wrap, lookups, copy, DayView.CALENDAR).hint)
    }

    @Test
    fun `a section the server left out of the wrap does not show`() {
        val bare = wrap.copy(persons = null, gem = null, tone = null, hours = null, domains = null, milestones = null)
        MonthSection.entries
            .filter { it != MonthSection.WEEKS }
            .forEach { assertFalse("$it", MonthSections.isShown(it, bare, lookups)) }
    }
}

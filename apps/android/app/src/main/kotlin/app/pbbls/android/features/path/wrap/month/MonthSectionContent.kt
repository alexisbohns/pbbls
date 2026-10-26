package app.pbbls.android.features.path.wrap.month

import androidx.compose.runtime.Composable
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.data.SnapAspectCache
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.week.rememberWrapCopy
import java.time.LocalDate
import java.time.ZoneId

/** One section of the month, as the page and the deck both draw it (#1242; iOS `MonthSectionContent`). */
@Composable
internal fun MonthSectionContent(
    section: MonthSection,
    wrap: MonthWrap,
    lookups: WrapLookups,
    dayView: DayView,
    zone: ZoneId,
    aspect: SnapAspectCache?,
    onWeek: (LocalDate) -> Unit,
    onOpen: (PathMoment) -> Unit,
    onMore: (MonthSection) -> Unit,
) {
    val copy = rememberWrapCopy()
    when (section) {
        MonthSection.WEEKS -> WeeksSummary(wrap, lookups, onWeek)
        MonthSection.MILESTONES -> MilestonesSection(wrap, lookups, zone, onOpen, aspect)
        MonthSection.PERSONS -> PersonsSection(wrap, lookups, dayView, onMore)
        MonthSection.DOMAINS -> DomainsSection(wrap, lookups, onMore)
        MonthSection.GEM -> EmotionGem(wrap, lookups, onMore)
        MonthSection.TONE, MonthSection.HOURS -> {
            // One head for both figures, over whichever comes first.
            if (section == wrap.sections.firstOrNull { it == MonthSection.TONE || it == MonthSection.HOURS }) {
                MonthSectionHead(copy.insights(), copy.insightsHint())
            }
            if (section == MonthSection.TONE) ToneFigure(wrap) else HoursFigure(wrap, lookups)
        }
    }
}

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
    /** Whether this is the first of the tone and the hours: the one the insights head leads. */
    leadsInsights: Boolean = section == wrap.sections.firstOrNull { it == MonthSection.TONE || it == MonthSection.HOURS },
    /** On a card the title band draws the head, so the section leaves its own out. */
    onCard: Boolean = false,
) {
    val copy = rememberWrapCopy()
    when (section) {
        MonthSection.WEEKS -> WeeksSummary(wrap, lookups, onWeek, onCard)
        MonthSection.MILESTONES -> MilestonesSection(wrap, lookups, zone, onOpen, aspect, onCard)
        MonthSection.PERSONS -> PersonsSection(wrap, lookups, dayView, onMore, onCard)
        MonthSection.DOMAINS -> DomainsSection(wrap, lookups, onMore, onCard)
        MonthSection.GEM -> EmotionGem(wrap, lookups, onMore, onCard)
        MonthSection.TONE, MonthSection.HOURS -> {
            // One head for both figures, over whichever comes first: on the page, drawn here; on a
            // card, the title band is that head, and the figure keeps its own (iOS).
            if (leadsInsights && !onCard) MonthSectionHead(copy.insights(), copy.insightsHint())
            val figureOnCard = onCard && !leadsInsights
            if (section == MonthSection.TONE) ToneFigure(wrap, lookups, figureOnCard) else HoursFigure(wrap, lookups, figureOnCard)
        }
    }
}

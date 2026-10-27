package app.pbbls.android.features.path.wrap.month

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.data.SnapAspectCache
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.week.rememberWrapCopy
import java.time.LocalDate
import java.time.ZoneId

/**
 * One section of the month, as the page and the deck both draw it (#1242; iOS `MonthSectionContent`).
 *
 * @param leadsInsights whether this is the first of the tone and the hours, the one the insights head
 * leads. The default is the plain page's rule (iOS `MonthPage`): the first of the two the server listed.
 * The deck passes [MonthSections.leadsInsights], over the sections it deals.
 * @param onCard whether a card draws this section. The card's title band then draws the section's head,
 * so the section leaves its own out; the one exception is the leading tone or hours figure, which keeps
 * its own head under the insights band (iOS), while a following one leaves its head to its card's band.
 * @param room on a card, the room under its title band, which the persons and the milestones share and at
 * whose foot the persons', the domains' and the gem's *more* stand (D13), and where the weeks stand as
 * columns (D17); null on the page, where rows keep their size.
 */
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
    leadsInsights: Boolean = section == wrap.sections.firstOrNull { it == MonthSection.TONE || it == MonthSection.HOURS },
    onCard: Boolean = false,
    room: Dp? = null,
) {
    when (section) {
        MonthSection.WEEKS -> WeeksSummary(wrap, lookups, onWeek, onCard, room)
        MonthSection.MILESTONES -> MilestonesSection(wrap, lookups, zone, onOpen, aspect, onCard, room)
        MonthSection.PERSONS -> PersonsSection(wrap, lookups, dayView, onMore, onCard, room)
        MonthSection.DOMAINS -> DomainsSection(wrap, lookups, onMore, onCard, room)
        MonthSection.GEM -> EmotionGem(wrap, lookups, onMore, onCard, room)
        MonthSection.TONE, MonthSection.HOURS -> {
            // On the page one head leads both figures; on a card the title band is that head instead.
            if (leadsInsights && !onCard) MonthSectionHead(MonthSections.insightsHead(rememberWrapCopy()))
            val figureOnCard = onCard && !leadsInsights
            if (section == MonthSection.TONE) ToneFigure(wrap, lookups, figureOnCard) else HoursFigure(wrap, lookups, figureOnCard)
        }
    }
}

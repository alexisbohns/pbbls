package app.pbbls.android.features.path.wrap.month

import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.model.wrap.PersonStat
import app.pbbls.android.core.model.wrap.WeekTile
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.copy.WrapCopy

/** A section's head: its label and its hint (#1242; iOS `MonthHead`). On a card, the card's title band draws it. */
internal data class MonthHead(
    val label: String,
    val hint: String?,
)

/** Each section's head and whether it draws, over the same conditions its composable draws under. */
internal object MonthSections {
    /** Whether [section] draws anything: the one condition the composable's own early return reads too. */
    fun isShown(
        section: MonthSection,
        wrap: MonthWrap,
        lookups: WrapLookups,
    ): Boolean =
        when (section) {
            MonthSection.WEEKS -> WeeksSummary.isShown(wrap)
            MonthSection.MILESTONES -> milestones(wrap, lookups).isNotEmpty()
            MonthSection.PERSONS -> wrap.persons != null
            MonthSection.DOMAINS -> wrap.domains.orEmpty().any { lookups.domain(it.domainId) != null }
            MonthSection.GEM -> wrap.gem != null
            MonthSection.TONE -> wrap.tone?.days?.isNotEmpty() == true
            MonthSection.HOURS ->
                wrap.hours
                    ?.items
                    .orEmpty()
                    .any { lookups.moment(it.momentId) != null }
        }

    /** The milestones the lookups resolve, in the server's order. */
    fun milestones(
        wrap: MonthWrap,
        lookups: WrapLookups,
    ): List<PathMoment> = wrap.milestones.orEmpty().mapNotNull(lookups.moment)

    /** The head the page and the card's title band share: one definition, so they cannot drift. */
    fun head(
        section: MonthSection,
        wrap: MonthWrap,
        lookups: WrapLookups,
        copy: WrapCopy,
        dayView: DayView = DayView.CIRCLE,
    ): MonthHead =
        when (section) {
            MonthSection.WEEKS -> MonthHead(copy.weeksHeader(WeeksSummary.weeks(wrap).size), copy.weeksHint(wrap.month))
            MonthSection.MILESTONES -> MonthHead(copy.milestonesHeader(milestones(wrap, lookups).size), copy.milestonesHint())
            MonthSection.PERSONS -> MonthHead(copy.tile(WeekTile.PERSONS), personsHint(wrap, lookups, dayView, copy))
            MonthSection.DOMAINS -> MonthHead(copy.tile(WeekTile.DOMAINS), null)
            MonthSection.GEM -> MonthHead(copy.tile(WeekTile.EMOTIONS), copy.gemHint())
            MonthSection.TONE -> MonthHead(copy.toneTitle(), copy.toneHint())
            MonthSection.HOURS -> MonthHead(copy.hoursTitle(), copy.hoursHint())
        }

    /** The persons the device knows a soul for, in the server's order. */
    fun namedPersons(
        wrap: MonthWrap,
        lookups: WrapLookups,
    ): List<PersonStat> = wrap.persons.orEmpty().filter { lookups.soul(it.soulId) != null }

    /** The persons' hint: *clockwise*, only around a ring that names someone. */
    fun personsHint(
        wrap: MonthWrap,
        lookups: WrapLookups,
        dayView: DayView,
        copy: WrapCopy,
    ): String? {
        val named = namedPersons(wrap, lookups).isNotEmpty()
        return if (dayView == DayView.CIRCLE && named) copy.clockwiseHint() else null
    }

    /**
     * Whether [section] leads the insights head among the sections that show: the deck deals only those
     * (iOS `MonthDeck.leadsInsights` over the dealt cards). The plain page keeps iOS's own rule, over the
     * listed sections, as [MonthSectionContent] defaults to.
     */
    fun leadsInsights(
        section: MonthSection,
        wrap: MonthWrap,
        lookups: WrapLookups,
    ): Boolean =
        section ==
            wrap.sections.firstOrNull {
                (it == MonthSection.TONE || it == MonthSection.HOURS) && isShown(it, wrap, lookups)
            }

    fun insightsHead(copy: WrapCopy) = MonthHead(copy.insights(), copy.insightsHint())
}

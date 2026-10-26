package app.pbbls.android.features.path.wrap.month

import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.model.wrap.WeekTile
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.copy.WrapCopy

/** A section's head: its label and its hint (#1242; iOS `MonthHead`). On a card, the title band draws it (D11). */
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

    fun head(
        section: MonthSection,
        wrap: MonthWrap,
        lookups: WrapLookups,
        dayView: DayView,
        copy: WrapCopy,
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

    /** The persons' hint: *clockwise*, only around a ring that names someone. */
    fun personsHint(
        wrap: MonthWrap,
        lookups: WrapLookups,
        dayView: DayView,
        copy: WrapCopy,
    ): String? {
        val named = wrap.persons.orEmpty().any { lookups.soul(it.soulId) != null }
        return if (dayView == DayView.CIRCLE && named) copy.clockwiseHint() else null
    }

    fun insightsHead(copy: WrapCopy) = MonthHead(copy.insights(), copy.insightsHint())
}

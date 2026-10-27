package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.BarSegment
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.ProportionBar
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.ui.MarkRow
import app.pbbls.android.core.ui.ReferenceStrings
import app.pbbls.android.core.ui.ReferenceType
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.week.rememberWrapCopy

private val BAR = 96.dp
private const val SHOWN = 5

/** The month's five domains, each with a bar against the first, and a *more* (#1207, D6). */
@Composable
internal fun DomainsSection(
    wrap: MonthWrap,
    lookups: WrapLookups,
    onMore: (MonthSection) -> Unit,
    onCard: Boolean = false,
) {
    val colors = PebblesTheme.spine.colors
    val copy = rememberWrapCopy()
    if (!MonthSections.isShown(MonthSection.DOMAINS, wrap, lookups)) return
    val domains = wrap.domains.orEmpty().mapNotNull { stat -> lookups.domain(stat.domainId)?.let { stat to it } }
    val max = domains.maxOf { it.first.count }.toFloat()
    Column {
        if (!onCard) MonthSectionHead(MonthSections.head(MonthSection.DOMAINS, wrap, lookups, copy))
        Column(Modifier.padding(horizontal = SECTION_PAD_X)) {
            domains.take(SHOWN).forEach { (stat, domain) ->
                val name = ReferenceStrings.referenceName(ReferenceType.DOMAIN, domain.slug, domain.name)
                MarkRow(
                    title = name,
                    count = stat.count,
                    description = "$name, ${stat.count}",
                    trailing = {
                        Box(Modifier.width(BAR)) {
                            ProportionBar(listOf(BarSegment(stat.count / max, colors.ink2), BarSegment(1f - stat.count / max, colors.rule)))
                        }
                    },
                )
            }
        }
        MonthMore(copy.moreDomains(domains.size)) { onMore(MonthSection.DOMAINS) }
    }
}

package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.BarSegment
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.ProportionBar
import app.pbbls.android.core.designsystem.RevealMotion
import app.pbbls.android.core.model.Domain
import app.pbbls.android.core.model.wrap.DomainStat
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.ui.MarkRow
import app.pbbls.android.core.ui.ReferenceStrings
import app.pbbls.android.core.ui.ReferenceType
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.intro.IntroBeat
import app.pbbls.android.features.path.wrap.intro.IntroKey
import app.pbbls.android.features.path.wrap.intro.IntroReveal
import app.pbbls.android.features.path.wrap.intro.IntroScore
import app.pbbls.android.features.path.wrap.intro.currentIntroReveal
import app.pbbls.android.features.path.wrap.intro.domains
import app.pbbls.android.features.path.wrap.intro.introReveal
import app.pbbls.android.features.path.wrap.week.rememberWrapCopy

private val BAR = 96.dp
private const val SHOWN = 5

/**
 * The month's five domains, each with a bar against the first, and a *more* (#1207, D6).
 *
 * In the month intro (#1242; iOS D10) each row lands in turn, its name, its count, its rule and its empty
 * track, then its bar fills to its share, *more* rising with the last. On a card ([room]) *more* stands at
 * its foot (D13); on the page, [room] is null.
 */
@Composable
internal fun DomainsSection(
    wrap: MonthWrap,
    lookups: WrapLookups,
    onMore: (MonthSection) -> Unit,
    onCard: Boolean = false,
    room: Dp? = null,
) {
    val colors = PebblesTheme.spine.colors
    val copy = rememberWrapCopy()
    val reveal = currentIntroReveal()
    if (!MonthSections.isShown(MonthSection.DOMAINS, wrap, lookups)) return
    val domains = DomainsSection.rows(wrap, lookups)
    val max = domains.maxOf { it.first.count }.toFloat()
    // On a card the section takes the room, so its weighted spacer can stand *more* at the foot.
    Column(if (room != null) Modifier.fillMaxHeight() else Modifier) {
        if (!onCard) MonthSectionHead(MonthSections.head(MonthSection.DOMAINS, wrap, lookups, copy))
        Column(Modifier.padding(horizontal = SECTION_PAD_X)) {
            domains.take(SHOWN).forEachIndexed { index, (stat, domain) ->
                // Settled (the page, or no clock), every part takes the core defaults: the row draws as ever.
                val row = index.takeIf { reveal !== IntroReveal.settled }
                val rowKey = remember(row) { row?.let(IntroKey::Row) }
                val name = ReferenceStrings.referenceName(ReferenceType.DOMAIN, domain.slug, domain.name)
                val share = stat.count / max
                // The name, the count and the track land as one (iOS `MarkRowReveal.row`).
                val lands = Modifier.introReveal(reveal, rowKey)
                MarkRow(
                    title = name,
                    count = stat.count,
                    description = "$name, ${stat.count}",
                    trailing = {
                        Box(Modifier.width(BAR)) {
                            if (row == null) {
                                ProportionBar(listOf(BarSegment(share, colors.ink2), BarSegment(1f - share, colors.rule)))
                            } else {
                                DomainGauge(share, reveal, row)
                            }
                        }
                    },
                    titleModifier = lands,
                    countModifier = lands,
                    trailingModifier = lands,
                    // The rule fades in with the row (iOS: the rule's `.fade` on the row's text key).
                    ruleAlpha = if (rowKey == null) ({ 1f }) else ({ RevealMotion.easeOut(reveal(rowKey).toDouble()).toFloat() }),
                )
            }
        }
        if (room != null) Spacer(Modifier.weight(1f))
        MonthMore(copy.moreDomains(domains.size), Modifier.introReveal(reveal, IntroKey.More)) { onMore(MonthSection.DOMAINS) }
    }
}

/**
 * A domain's gauge in the intro: its bar filling to [share] on [row]'s `Gauge`, eased out, the track taking
 * the rest. Its own scope, so only the bar recomposes while it reads its fill.
 */
@Composable
private fun DomainGauge(
    share: Float,
    reveal: IntroReveal,
    row: Int,
) {
    val colors = PebblesTheme.spine.colors
    val key = remember(row) { IntroKey.Gauge(row) }
    val grown = share * RevealMotion.easeOut(reveal(key).toDouble()).toFloat()
    ProportionBar(listOf(BarSegment(grown, colors.ink2), BarSegment(1f - grown, colors.rule)))
}

/** The domains' pure parts (#1242; iOS `DomainsSection`'s statics). */
internal object DomainsSection {
    /** The domains the device knows, with their names, in the server's order. */
    fun rows(
        wrap: MonthWrap,
        lookups: WrapLookups,
    ): List<Pair<DomainStat, Domain>> = wrap.domains.orEmpty().mapNotNull { stat -> lookups.domain(stat.domainId)?.let { stat to it } }

    /** The domains' intro (iOS D10): the head, then each of the five drawn, its row and its gauge filling, *more*. */
    fun introBeats(
        wrap: MonthWrap,
        lookups: WrapLookups,
    ): List<IntroBeat> {
        val counts = rows(wrap, lookups).take(SHOWN).map { it.first.count }
        val top = counts.maxOrNull() ?: 0
        if (top <= 0) return IntroScore.domains(emptyList(), more = true)
        return IntroScore.domains(counts.map { it.toDouble() / top }, more = true)
    }
}

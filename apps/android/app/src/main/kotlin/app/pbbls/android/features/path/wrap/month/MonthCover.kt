package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.ui.ReferenceStrings
import app.pbbls.android.core.ui.ReferenceType
import app.pbbls.android.core.ui.SoulMark
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.intro.IntroBeat
import app.pbbls.android.features.path.wrap.intro.IntroKey
import app.pbbls.android.features.path.wrap.intro.IntroLanding
import app.pbbls.android.features.path.wrap.intro.IntroScore
import app.pbbls.android.features.path.wrap.intro.currentIntroReveal
import app.pbbls.android.features.path.wrap.intro.introReveal
import app.pbbls.android.features.path.wrap.week.familyNames
import app.pbbls.android.features.path.wrap.week.rememberWrapCopy
import app.pbbls.android.features.path.wrap.week.spotInk

private val PAD_X = 20.dp
private val PAD_TOP = 20.dp
private val GAP = 10.dp
private val SOUL = 15.dp
private const val SOULS_IN_RING = 8

/**
 * The month's cover (#1207, D7): the garland with the month inside it (its place in the year, its
 * name, the year, the souls' glyphs), then the summary, the persons in order of appearance and the
 * colophon of inks and papers. Only the server's numbers and orders; the words are `WrapCopy`'s.
 * In the month's intro (#1242) it is the first card: the name, the garland, then the words.
 */
@Composable
internal fun MonthCover(
    wrap: MonthWrap,
    lookups: WrapLookups,
    modifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val copy = rememberWrapCopy()
    val cover = wrap.cover ?: return
    val souls = cover.soulsInOrder.mapNotNull(lookups.soul)
    val reveal = currentIntroReveal()

    Column(
        modifier.fillMaxWidth().padding(horizontal = PAD_X).padding(top = PAD_TOP),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GAP),
    ) {
        Garland(wrap, lookups, reveal = reveal) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    copy.monthKicker(wrap.month),
                    style = type.sectionLabel,
                    color = colors.ink2,
                    modifier = Modifier.introReveal(reveal, IntroKey.Kicker),
                )
                Text(
                    copy.monthName(wrap.month),
                    style = type.monthName,
                    color = colors.ink,
                    modifier = Modifier.introReveal(reveal, IntroKey.MonthName).semantics { heading() },
                )
                Text(
                    wrap.month.year.toString(),
                    style = type.contentsTime,
                    color = colors.ink2,
                    modifier = Modifier.introReveal(reveal, IntroKey.Year),
                )
                if (souls.isNotEmpty()) {
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        souls.take(SOULS_IN_RING).forEachIndexed { i, soul ->
                            SoulMark(soul.glyph, colors.ink, Modifier.size(SOUL).introReveal(reveal, IntroKey.Soul(i), IntroLanding.POP))
                        }
                    }
                }
            }
        }
        Text(
            copy.line(wrap.pebbles, wrap.people, live = false, hasMoments = true),
            style = type.summary,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.introReveal(reveal, IntroKey.Summary),
        )
        if (souls.isNotEmpty()) {
            Column(Modifier.introReveal(reveal, IntroKey.Names), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(copy.personsInOrderLabel(), style = type.sectionHint, color = colors.ink3)
                Text(souls.joinToString(" · ") { it.name }, style = type.soulName, color = colors.ink, textAlign = TextAlign.Center)
            }
        }
        Colophon(wrap, lookups, Modifier.introReveal(reveal, IntroKey.Colophon))
    }
}

/** The cover's beats, over what it draws (iOS `MonthCoverView.introBeats`): the ring's souls, the lines shown. */
internal fun coverBeats(
    wrap: MonthWrap,
    lookups: WrapLookups,
): List<IntroBeat> {
    val cover = wrap.cover ?: return emptyList()
    val souls = cover.soulsInOrder.count { lookups.soul(it) != null }
    // As `Colophon`: every ink is named (an unknown family by its slug), a paper only when known.
    val colophon = cover.inks.isNotEmpty() || cover.papers.any { lookups.domain(it) != null }
    val lines =
        buildList {
            add(IntroKey.Summary)
            if (souls > 0) add(IntroKey.Names)
            if (colophon) add(IntroKey.Colophon)
        }
    return IntroScore.cover(cover.days, wrap.daysInMonth, wrap.numberedDays(), minOf(souls, SOULS_IN_RING), lines)
}

/** "Printed in *joy*, *peace* & *pride*, … on family, identity & friends.", each ink in its family's colour. */
@Composable
private fun Colophon(
    wrap: MonthWrap,
    lookups: WrapLookups,
    modifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val copy = rememberWrapCopy()
    val cover = wrap.cover ?: return
    val inks = familyNames(cover.inks, lookups).map { it.lowercase() }
    val papers =
        cover.papers.mapNotNull { id ->
            lookups.domain(id)?.let { ReferenceStrings.referenceName(ReferenceType.DOMAIN, it.slug, it.name).lowercase() }
        }
    if (inks.isEmpty() && papers.isEmpty()) return
    val sentence = copy.colophon(inks, papers)
    val spans = cover.inks.zip(inks).map { (slug, name) -> name to spotInk(lookups.family(slug)?.palette) }
    Text(text = inked(sentence, spans), modifier = modifier, style = type.still, color = colors.ink2, textAlign = TextAlign.Center)
}

/** Colours each name where it first appears after the previous one. */
private fun inked(
    sentence: String,
    spans: List<Pair<String, androidx.compose.ui.graphics.Color>>,
): AnnotatedString =
    buildAnnotatedString {
        append(sentence)
        var from = 0
        spans.forEach { (name, color) ->
            val at = sentence.indexOf(name, from, ignoreCase = true)
            if (at >= 0) {
                addStyle(SpanStyle(color = color), at, at + name.length)
                from = at + name.length
            }
        }
    }

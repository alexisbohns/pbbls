package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.CalendarLabels
import app.pbbls.android.core.designsystem.DotStyle
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.RevealMotion
import app.pbbls.android.core.designsystem.SpineLayoutTokens
import app.pbbls.android.core.designsystem.dotMark
import app.pbbls.android.core.model.wrap.CoverPebble
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.ui.marks.CairnGeometry
import app.pbbls.android.core.ui.marks.CairnMark
import app.pbbls.android.core.ui.marks.CairnState
import app.pbbls.android.core.ui.render.stone.SealPrint
import app.pbbls.android.core.ui.render.stone.SealVariant
import app.pbbls.android.core.ui.render.stone.drawSeal
import app.pbbls.android.core.ui.render.stone.rememberSealPrint
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.copy.WrapCopy
import app.pbbls.android.features.path.wrap.intro.IntroKey
import app.pbbls.android.features.path.wrap.intro.IntroLanding
import app.pbbls.android.features.path.wrap.intro.IntroReveal
import app.pbbls.android.features.path.wrap.intro.currentIntroReveal
import app.pbbls.android.features.path.wrap.intro.introReveal
import app.pbbls.android.features.path.wrap.week.rememberWrapCopy
import app.pbbls.android.features.path.wrap.week.valenceOf
import java.time.LocalDate
import java.time.YearMonth

/** A column's date: its Monday's number over its month. */
private val LABEL = 34.dp

/** The cairn's room at a column's foot. */
private val CAIRN_ROOM = 64.dp

/** Between a column's date, its days and its cairn. */
private val GAP = 8.dp
private val CELL_MIN = 18.dp
private val CELL_CAP = 40.dp

/** The weekdays' margin. */
private val LEGEND = 16.dp
private val SOCKET = 6.8.dp
private val PIP_GAP = 2.dp
private val PIP_CAP = 16.dp

/** A pebble's width in its day, by its intensity, 1…3, as a share of the day's pip. */
private val PIP_SCALES = floatArrayOf(0.85f, 1f, 1.15f)

/** How far a popping seal's layer reaches past its box: its rim, half outside the outline, and the antialiasing. */
private val SEAL_LAYER_PAD = 2.dp

private const val DAYS = 7

/**
 * The month's weeks on a card (#1242; iOS `WeeksColumns`, D17): a column per week, Monday first down it under the
 * week's date, each day a dotted slot or up to three of its pebbles, inked as on the garland, and the week's cairn
 * at the column's foot. The weekdays' letters stand in a margin on the left. A tap on a column focuses its week in
 * the strip. In the intro, one week at a time: its date, its days down the column (a slot silently, each pebble
 * with a tick), then its cairn stone by stone. [room] is the card's: the days' cells share what the date and the
 * cairn leave of it, within their bounds.
 */
@Composable
internal fun WeeksColumns(
    wrap: MonthWrap,
    lookups: WrapLookups,
    room: Dp,
    onWeek: (LocalDate) -> Unit,
) {
    val columns = remember(wrap) { WeekColumn.columns(wrap) }
    val cell = ((room - LABEL - CAIRN_ROOM - GAP * 2) / DAYS).coerceIn(CELL_MIN, CELL_CAP)
    Row(
        Modifier
            .fillMaxWidth()
            .height(LABEL + GAP + cell * DAYS + GAP + CAIRN_ROOM)
            .padding(horizontal = SECTION_PAD_X),
        verticalAlignment = Alignment.Top,
    ) {
        WeekdayMargin(cell)
        columns.forEachIndexed { index, column ->
            key(column.week.weekStart) {
                WeekColumnView(column, index, cell, lookups, wrap.month, onWeek, Modifier.weight(1f))
            }
        }
    }
}

/** Monday first, level with the columns' days. Decorative: each column says its week. */
@Composable
private fun WeekdayMargin(cell: Dp) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val locale = LocalConfiguration.current.locales[0]
    // Any Monday: the letters only need the week's order.
    val letters = remember(locale) { CalendarLabels.weekdayLetters(LocalDate.of(2026, 10, 5), locale) }
    Column(Modifier.width(LEGEND).padding(top = LABEL + GAP).clearAndSetSemantics {}) {
        letters.forEach { letter ->
            Box(Modifier.fillMaxWidth().height(cell), contentAlignment = Alignment.Center) {
                Text(letter, style = type.dayLetter, color = colors.ink3, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** One week's column: its Monday's date, its days, its cairn; one button saying the week, as its list row does. */
@Composable
private fun WeekColumnView(
    column: WeekColumn,
    index: Int,
    cell: Dp,
    lookups: WrapLookups,
    month: YearMonth,
    onWeek: (LocalDate) -> Unit,
    modifier: Modifier,
) {
    val copy = rememberWrapCopy()
    val reveal = currentIntroReveal()
    val week = column.week
    val range = copy.range(month, week.from, week.to)
    val line = if (week.pebbles == 0) copy.emptyWeek() else copy.line(week.pebbles, week.soulIds.size, live = false, hasMoments = true)
    val description = "$range, $line"
    val focus = { onWeek(week.weekStart) }
    Column(
        modifier
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick {
                    focus()
                    true
                }
            }.clickable(role = Role.Button, onClick = focus),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GAP),
    ) {
        ColumnDate(week.weekStart, copy, reveal, index)
        ColumnDays(column, index, cell, lookups, reveal)
        Box(Modifier.height(CAIRN_ROOM), contentAlignment = Alignment.TopCenter) { ColumnCairn(column, index, reveal) }
    }
}

/**
 * The Monday's number over its month's abbreviation, never past the default text size (iOS caps it at `.large`).
 * It lands on its week's `Words` as two lines, not as the label's box: they stand past it, and a fading layer
 * is clipped to its own bounds.
 */
@Composable
private fun ColumnDate(
    monday: LocalDate,
    copy: WrapCopy,
    reveal: IntroReveal,
    week: Int,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val density = LocalDensity.current
    val capped = remember(density) { Density(density.density, density.fontScale.coerceAtMost(1f)) }
    Box(Modifier.height(LABEL), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalDensity provides capped) {
            // Its own height, centred on the label's, as iOS's frame lets it stand past it.
            Column(
                Modifier.wrapContentHeight(unbounded = true).introReveal(reveal, IntroKey.Words(week)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(monday.dayOfMonth.toString(), style = type.dayNumeral, color = colors.ink, maxLines = 1, softWrap = false)
                Text(copy.weekColumnMonth(monday), style = type.dayLetter, color = colors.ink3, maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * The column's seven days, a cell each: an empty day's socket, popping on its `Rest`; a day's pebbles, at most
 * three side by side, each a seal sized by its intensity popping on its `Pip`. Built once per size and
 * composition: a frame of the intro only reads the reveal and draws.
 */
@Composable
private fun ColumnDays(
    column: WeekColumn,
    index: Int,
    cell: Dp,
    lookups: WrapLookups,
    reveal: IntroReveal,
) {
    val colors = PebblesTheme.spine.colors
    val placed =
        remember(column) {
            column.days.flatMapIndexed { day, kind ->
                val shown = (kind as? WeekColumnDay.Pebbles)?.pebbles?.take(WeekColumn.PIPS).orEmpty()
                shown.mapIndexed { place, pebble -> PlacedPip(day, place, shown.size, pebble) }
            }
        }
    // Inked as on the garland: an emotion the device doesn't know prints blank.
    val prints =
        placed.map { place ->
            key(place.pebble.momentId) {
                val p = place.pebble
                rememberSealPrint(
                    valence = valenceOf(sizeOfIntensity(p.intensity), p.polarity),
                    palette = p.emotionId?.let { lookups.emotion(it)?.palette },
                    glyph = null,
                    knockout = colors.ground,
                    variant = SealVariant.MINI,
                )
            }
        }
    // The one paint a popping seal's layer fades through, so a frame allocates none.
    val layerPaint = remember { Paint() }
    Spacer(
        Modifier.fillMaxWidth().height(cell * DAYS).drawWithCache {
            val cellPx = cell.toPx()
            val gap = PIP_GAP.toPx()
            val pip = minOf(cellPx * 0.5f, (size.width - 2 * gap) / WeekColumn.PIPS * 0.9f, PIP_CAP.toPx())
            val socket = SOCKET.toPx()
            val dotRing = SpineLayoutTokens.DOT_RING.toPx()
            val middle = size.width / 2
            val sockets =
                column.days.withIndex().filter { it.value == WeekColumnDay.Empty }.map { (day, _) ->
                    ColumnSocket(IntroKey.Rest(index, day), Offset(middle, (day + 0.5f) * cellPx))
                }
            val pad = SEAL_LAYER_PAD.toPx()
            val seals =
                placed.mapIndexed { i, place ->
                    val print = prints[i]
                    val w = pip * PIP_SCALES[(place.pebble.intensity - 1).coerceIn(0, 2)]
                    val h = w / print.aspectRatio
                    val span = place.count * pip + (place.count - 1) * gap
                    ColumnSeal(
                        key = IntroKey.Pip(index, place.day, place.place),
                        at = Offset(middle - span / 2 + place.place * (pip + gap) + pip / 2, (place.day + 0.5f) * cellPx),
                        print = print,
                        width = w,
                        height = h,
                        layer = Rect(-pad, -pad, w + pad, h + pad),
                    )
                }
            onDrawBehind {
                sockets.forEach { (key, at) ->
                    val shown = reveal(key)
                    if (shown <= 0f) return@forEach
                    if (shown < 1f) {
                        val p = shown.toDouble()
                        val color = colors.ink3.copy(alpha = colors.ink3.alpha * RevealMotion.popOpacity(p).toFloat())
                        dotMark(at, socket * RevealMotion.popScale(p).toFloat(), DotStyle.SOCKET, color, dotRing)
                    } else {
                        dotMark(at, socket, DotStyle.SOCKET, colors.ink3, dotRing)
                    }
                }
                seals.forEach { seal ->
                    val landed = reveal(seal.key)
                    if (landed <= 0f) return@forEach
                    withTransform({
                        translate(seal.at.x, seal.at.y)
                        if (landed < 1f) {
                            val pop = RevealMotion.popScale(landed.toDouble()).toFloat()
                            scale(pop, pop, Offset.Zero)
                        }
                        translate(-seal.width / 2, -seal.height / 2)
                    }) {
                        val opacity = RevealMotion.popOpacity(landed.toDouble()).toFloat()
                        if (opacity < 1f) {
                            // The seal overlaps its own paths, so it fades as one: a layer, only while it fades in.
                            layerPaint.alpha = opacity
                            drawContext.canvas.saveLayer(seal.layer, layerPaint)
                            drawSeal(seal.print, seal.width)
                            drawContext.canvas.restore()
                        } else {
                            drawSeal(seal.print, seal.width)
                        }
                    }
                }
            }
        },
    )
}

/** The week's cairn, built stone by stone; an empty week's dotted stone, popping on its first stone's beat. */
@Composable
private fun ColumnCairn(
    column: WeekColumn,
    index: Int,
    reveal: IntroReveal,
) {
    val colors = PebblesTheme.spine.colors
    val week = column.week
    when {
        week.pebbles == 0 ->
            CairnMark(emptyList(), CairnState.LIVE, colors.ink3, Modifier.introReveal(reveal, IntroKey.Cairn(index, 0), IntroLanding.POP))
        reveal === IntroReveal.settled -> CairnMark(week.days, CairnState.SETTLED, colors.ink2)
        else -> {
            // The keys once per composition, so a frame reads the reveal without building one.
            val stones = remember(index, week.days) { List(CairnGeometry.widths(week.days).size) { IntroKey.Cairn(index, it) } }
            CairnMark(week.days, CairnState.SETTLED, colors.ink2, built = { stone -> stones.getOrNull(stone)?.let { reveal(it) } ?: 1f })
        }
    }
}

/** A pebble of a day, at [place] of the [count] the day shows. */
private class PlacedPip(
    val day: Int,
    val place: Int,
    val count: Int,
    val pebble: CoverPebble,
)

/** An empty day's socket: its beat's key and its centre. */
private data class ColumnSocket(
    val key: IntroKey,
    val at: Offset,
)

/** A pebble's seal in its day: its beat's key, its centre, its print and size, and the box its fading layer covers. */
private class ColumnSeal(
    val key: IntroKey,
    val at: Offset,
    val print: SealPrint,
    val width: Float,
    val height: Float,
    val layer: Rect,
)

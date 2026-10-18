package app.pbbls.android.features.path.spine

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.data.SnapAspectCache
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.rememberReduceMotion
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.model.WeekRollEntry
import app.pbbls.android.core.ui.render.stone.StoneMotion
import app.pbbls.android.features.path.WeekRollBuilder
import app.pbbls.android.features.path.spine.SpineLayout.CARD_ANCHOR
import app.pbbls.android.features.path.spine.SpineLayout.DAY_PAD_BOTTOM
import app.pbbls.android.features.path.spine.SpineLayout.DAY_PAD_TOP
import app.pbbls.android.features.path.spine.SpineLayout.FEED_BOTTOM
import app.pbbls.android.features.path.spine.SpineLayout.FEED_TOP
import app.pbbls.android.features.path.spine.SpineLayout.PEBBLE_GAP
import app.pbbls.android.features.path.spine.SpineLayout.SEAM_HEIGHT
import app.pbbls.android.features.path.spine.SpineLayout.SEAM_LABEL_OVERHANG
import app.pbbls.android.features.path.spine.SpineLayout.SPINE_X
import app.pbbls.android.features.path.spine.SpineLayout.STILL_MIN_HEIGHT
import app.pbbls.android.features.path.spine.logic.EntryTypes
import app.pbbls.android.features.path.spine.logic.PersonsOfWeek
import app.pbbls.android.features.path.spine.logic.SpineDay
import app.pbbls.android.features.path.spine.logic.SpineWeek
import app.pbbls.android.features.path.spine.logic.SpineWeekBuilder
import app.pbbls.android.features.path.spine.logic.WeekWordsBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** The line down the margin, the length of every day. */
private val LINE_WIDTH = 1.dp

/**
 * One week of the Path as a book (#1122, spec §Architecture; the web's `SpineWeek.tsx`):
 * the verso — masthead, opening, the persons of the week, the contents — then the recto:
 * the raked ground with every day of the week opened by its seam, oldest first, each
 * pebble a lit stone on the line, and the mouth where the week ends. Only the current week
 * can be empty; its recto is then the fresh-week invitation.
 *
 * One `LazyColumn` over the rows `WeekItems` lays out, every day row on the shared ground
 * (the rake's phase runs over the rows' heights, so it crosses them without a seam) and on
 * the line. The persons and the contents follow the reading: the day crossing a line 35 %
 * down the viewport turns ink in both, read inside the letters and the rows so the list
 * itself never recomposes for it. A contents row scrolls its stone to the middle of the
 * viewport and presses it without opening (spec D8).
 *
 * The day geometry is the web's, hairline to content: 58 dp above a day's first pebble, 38
 * between pebbles, 34 below its last; a still day is at least 48 dp long with *still* 14 dp
 * under its hairline. The seam is [SEAM_LABEL_OVERHANG] taller than its hairline sits, for the
 * label hung above it, and that overhang comes off the previous day's bottom padding —
 * never off the day's own top — so the first day's seam follows the verso by [FEED_TOP]
 * minus the overhang, and the last day keeps its full 34 before the mouth. The day's top
 * padding rides on the seam row, so on a day with moments the 52 dp label (which the seam
 * never measures) overflows into that padding and nothing drawn later covers it; a still
 * day measures its label instead and grows with it, so nothing is ever cut at a large font
 * (52 rather than the web's 48 at the default one).
 *
 * The Spine is left-to-right whatever the locale: the line, the leaders and the cairn sit
 * at an absolute [SPINE_X], no RTL locale ships, and mirroring the page is a later task.
 *
 * @param today the local date, for which days exist and whether this is the current week.
 * @param paletteFor the pebble's emotion palette; null for a blank stone.
 * @param aspect the session's photo-ratio cache; null in previews and the lab.
 * @param onPreviousWeek absent on the first week there is; [onNextWeek] on the last.
 * @param listState hoisted so a preview can open the page at a row.
 * @param ratioOverride a primed photo ratio by snap prefix, for previews with no cache.
 */
@Composable
fun SpineWeek(
    entry: WeekRollEntry,
    today: LocalDate,
    soulsById: Map<String, SoulWithGlyph>,
    paletteFor: (PathMoment) -> EmotionPalette?,
    aspect: SnapAspectCache?,
    onOpen: (PathMoment) -> Unit,
    onRequestDelete: (PathMoment) -> Unit,
    onPreviousWeek: (() -> Unit)?,
    onNextWeek: (() -> Unit)?,
    onCreatePebble: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    ratioOverride: ((String) -> Float?)? = null,
) {
    val colors = PebblesTheme.spine.colors
    val density = LocalDensity.current
    val locale = LocalConfiguration.current.locales[0]
    val zone = remember { ZoneId.systemDefault() }
    val scope = rememberCoroutineScope()
    val reduceMotion by rememberUpdatedState(rememberReduceMotion())

    val week = remember(entry, today, zone) { SpineWeekBuilder.build(entry, zone, today) }
    val persons = remember(entry, zone, locale) { PersonsOfWeek.of(entry.moments, entry.weekStart, zone, locale) }
    val personsOrder = remember(persons) { persons.map { it.soul.id } }
    val words = remember(entry.weekStart, locale) { WeekWordsBuilder.of(entry.weekStart, locale) }
    val current = remember(entry.weekStart, today) { WeekRollBuilder.weekStart(today) == entry.weekStart }
    val empty = entry.moments.isEmpty()
    val items = remember(week, persons, empty) { WeekItems(week, hasPersons = persons.isNotEmpty(), empty = empty) }

    val readingDay = rememberReadingDay(listState) { key -> key?.let(items.dayOfKey::get) }
    val firstDay = week.days.firstOrNull()?.index
    val dayRead = remember(readingDay, firstDay) { { readingDay.value ?: firstDay } }

    // The ground's phase: each day row records its height, and the row after it starts
    // the rake where it left off. Draw-time reads, so a card settling to its photo's ratio
    // redraws the rows under it and recomposes nothing.
    val tile = rememberRakeTile()
    val heights = remember(items) { mutableStateMapOf<Int, Int>() }

    // The contents → press: scroll the stone to the middle, then ask it to press (never open).
    val pressRequests = remember { StonePressRequests() }
    val goJob = remember { JobHolder() }
    DisposableEffect(items) { onDispose { goJob.job?.cancel() } }
    val go: (PathMoment) -> Unit = { moment ->
        val index = items.indexOf(moment.id)
        if (index >= 0) {
            val row = items.rows[index] as WeekRow.Pebble
            goJob.job?.cancel()
            goJob.job =
                scope.launch {
                    val offset = stoneScrollOffset(listState, density, moment, row.place)
                    if (reduceMotion) {
                        listState.scrollToItem(index, offset)
                        // The stone composes on the frame the scroll lands; ask it after.
                        withFrameNanos {}
                    } else {
                        listState.animateScrollToItem(index, offset)
                        delay(StoneMotion.GO_PRESS_DELAY_MS)
                    }
                    pressRequests.request(moment.id)
                }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = PaddingValues(bottom = FEED_BOTTOM),
        ) {
            items(items.rows, key = { it.key }, contentType = { it::class }) { row ->
                when (row) {
                    WeekRow.Masthead -> WeekMasthead(words = words, onPreviousWeek = onPreviousWeek)
                    WeekRow.Opening -> WeekOpening(words = words, pebbleCount = week.pebbleCount, peopleCount = persons.size)
                    WeekRow.Persons ->
                        PersonsOfWeek(persons = persons, weekStart = entry.weekStart, soulsById = soulsById, readingDay = dayRead)
                    WeekRow.Contents -> WeekContents(week = week, zone = zone, paletteFor = paletteFor, readingDay = dayRead, onGo = go)
                    WeekRow.Empty -> SpineEmptyWeek(onCreatePebble = onCreatePebble)
                    is WeekRow.Seam -> {
                        val seam =
                            Modifier
                                .then(SpineGroundElement(tile, if (row.first) FEED_TOP else 0.dp, row.ground, heights, colors.line))
                                .padding(top = if (row.first) FEED_TOP - SEAM_LABEL_OVERHANG else 0.dp)
                        if (row.day.still) {
                            // The seam measures its own 48 and its label; the last day has no next
                            // seam to hand the overhang to, so it keeps its full 48 to the mouth.
                            DaySeam(
                                day = row.day,
                                modifier = if (row.last) seam.heightIn(min = STILL_MIN_HEIGHT + SEAM_LABEL_OVERHANG) else seam,
                            )
                        } else {
                            DaySeam(day = row.day, modifier = seam.padding(bottom = DAY_PAD_TOP - SEAM_HEIGHT))
                        }
                    }
                    is WeekRow.Pebble ->
                        SpinePebble(
                            moment = row.moment,
                            palette = paletteFor(row.moment),
                            soulsById = soulsById,
                            personsOrder = personsOrder,
                            aspect = aspect,
                            pressRequests = pressRequests,
                            onOpen = onOpen,
                            onRequestDelete = onRequestDelete,
                            modifier =
                                Modifier
                                    .then(SpineGroundElement(tile, 0.dp, row.ground, heights, colors.line))
                                    .padding(
                                        top = pebbleTopPadding(row.place),
                                        bottom =
                                            when {
                                                !row.lastOfDay -> 0.dp
                                                row.lastDay -> DAY_PAD_BOTTOM
                                                else -> DAY_PAD_BOTTOM - SEAM_LABEL_OVERHANG
                                            },
                                    ),
                            ratioOverride = row.moment.firstSnapPath?.let { prefix -> ratioOverride?.invoke(prefix) },
                        )
                    is WeekRow.Mouth ->
                        WeekMouth(
                            words = words,
                            current = current,
                            onNextWeek = onNextWeek,
                            // The mouth draws its own last run of the line.
                            modifier = Modifier.then(SpineGroundElement(tile, 0.dp, row.ground, heights, line = null)),
                        )
                }
            }
        }
    }
}

/** The one in-flight contents → press, cancelled by the next and when the week changes. */
private class JobHolder {
    var job: Job? = null
}

/** Between two pebbles of a day; the day's first follows its seam's padding instead. */
private fun pebbleTopPadding(placeInDay: Int): Dp = if (placeInDay == 0) 0.dp else PEBBLE_GAP

/**
 * The `scrollOffset` that puts [moment]'s stone in the middle of the viewport: the row's
 * top lands half a viewport up from the stone's centre, which sits its top padding plus the
 * anchor into the row. Negative, as `LazyListState` counts an offset from the item's top.
 */
private fun stoneScrollOffset(
    listState: LazyListState,
    density: Density,
    moment: PathMoment,
    placeInDay: Int,
): Int {
    val viewport = listState.layoutInfo.viewportSize.height
    val anchor =
        if (moment.firstSnapPath != null) {
            CARD_ANCHOR
        } else {
            EntryTypes.entry(moment.valence.intensity, moment.valence.polarity).anchorDp.dp
        }
    val centre = with(density) { (pebbleTopPadding(placeInDay) + anchor).roundToPx() }
    return -(viewport / 2 - centre)
}

/** One row of the week's list; [key] is the lazy key, and the class the content type. */
private sealed interface WeekRow {
    val key: Any

    data object Masthead : WeekRow {
        override val key = "masthead"
    }

    data object Opening : WeekRow {
        override val key = "opening"
    }

    data object Persons : WeekRow {
        override val key = "persons"
    }

    data object Contents : WeekRow {
        override val key = "contents"
    }

    data object Empty : WeekRow {
        override val key = "empty"
    }

    /** A day's seam; [ground] is its place among the rows on the ground. */
    data class Seam(
        val day: SpineDay,
        val first: Boolean,
        val last: Boolean,
        val ground: Int,
    ) : WeekRow {
        override val key = "day-${day.index}"
    }

    /** A moment; [place] is 0 for its day's first. */
    data class Pebble(
        val moment: PathMoment,
        val place: Int,
        val lastOfDay: Boolean,
        val lastDay: Boolean,
        val ground: Int,
    ) : WeekRow {
        override val key = moment.id
    }

    data class Mouth(
        val ground: Int,
    ) : WeekRow {
        override val key = "mouth"
    }
}

/** The rows of the list in order, computed once per week: the one place the order lives. */
private class WeekItems(
    week: SpineWeek,
    hasPersons: Boolean,
    empty: Boolean,
) {
    val rows: List<WeekRow>

    /** The day a row belongs to — a seam and its moments — for the reading day. */
    val dayOfKey: Map<Any, Int>

    init {
        val rows = ArrayList<WeekRow>()
        val dayOfKey = HashMap<Any, Int>()
        rows += WeekRow.Masthead
        rows += WeekRow.Opening
        if (hasPersons) rows += WeekRow.Persons
        if (week.readingOrder.isNotEmpty()) rows += WeekRow.Contents
        if (empty) {
            rows += WeekRow.Empty
        } else {
            var ground = 0
            week.days.forEachIndexed { position, day ->
                val seam = WeekRow.Seam(day, first = position == 0, last = position == week.days.lastIndex, ground = ground++)
                rows += seam
                dayOfKey[seam.key] = day.index
                day.moments.forEachIndexed { place, moment ->
                    rows +=
                        WeekRow.Pebble(
                            moment = moment,
                            place = place,
                            lastOfDay = place == day.moments.lastIndex,
                            lastDay = position == week.days.lastIndex,
                            ground = ground++,
                        )
                    dayOfKey[moment.id] = day.index
                }
            }
            rows += WeekRow.Mouth(ground)
        }
        this.rows = rows
        this.dayOfKey = dayOfKey
    }

    /** The moment's row index in the list, or −1. */
    fun indexOf(momentId: String): Int = rows.indexOfFirst { it is WeekRow.Pebble && it.moment.id == momentId }
}

/**
 * A row on the ground: records its height for the rows below, draws the rake at the phase
 * the rows above left and the line down the margin, both from [topInset]. A node, not a
 * lambda chain, so the modifier compares equal across recompositions and the row keeps
 * its brush.
 */
private data class SpineGroundElement(
    val tile: Bitmap,
    val topInset: Dp,
    val ground: Int,
    val heights: SnapshotStateMap<Int, Int>,
    val line: Color?,
) : ModifierNodeElement<SpineGroundNode>() {
    override fun create() = SpineGroundNode(tile, topInset, ground, heights, line)

    override fun update(node: SpineGroundNode) = node.update(tile, topInset, ground, heights, line)

    override fun InspectorInfo.inspectableProperties() {
        name = "spineGround"
        properties["ground"] = ground
    }
}

private class SpineGroundNode(
    private var tile: Bitmap,
    private var topInset: Dp,
    private var ground: Int,
    private var heights: SnapshotStateMap<Int, Int>,
    private var line: Color?,
) : Modifier.Node(),
    DrawModifierNode,
    LayoutAwareModifierNode {
    private var brush: ShaderBrush? = null
    private var brushWidth = 0f

    fun update(
        tile: Bitmap,
        topInset: Dp,
        ground: Int,
        heights: SnapshotStateMap<Int, Int>,
        line: Color?,
    ) {
        if (tile !== this.tile) brush = null
        this.tile = tile
        this.topInset = topInset
        this.ground = ground
        this.heights = heights
        this.line = line
        invalidateDraw()
    }

    override fun onRemeasured(size: IntSize) {
        heights[ground] = size.height
    }

    override fun ContentDrawScope.draw() {
        val inset = topInset.toPx()
        if (size.width > 0f && size.height > inset) {
            val rake =
                brush?.takeIf { brushWidth == size.width }
                    ?: rakeBrush(tile, size.width, this).also {
                        brush = it
                        brushWidth = size.width
                    }
            // The shader sits in canvas space: shifting the canvas up by the phase and the
            // rect down by it keeps the rect in place while the tile's rows move on.
            val shift = (0 until ground).sumOf { heights[it] ?: 0 }.toFloat().mod(tile.height.toFloat())
            translate(top = -shift) {
                drawRect(rake, topLeft = Offset(0f, inset + shift), size = Size(size.width, size.height - inset))
            }
            line?.let { ink ->
                val x = SPINE_X.toPx()
                drawLine(ink, Offset(x, inset), Offset(x, size.height), LINE_WIDTH.toPx())
            }
        }
        drawContent()
    }
}

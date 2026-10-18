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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

private const val KEY_MASTHEAD = "masthead"
private const val KEY_OPENING = "opening"
private const val KEY_PERSONS = "persons"
private const val KEY_CONTENTS = "contents"
private const val KEY_EMPTY = "empty"
private const val KEY_MOUTH = "mouth"

private fun seamKey(day: SpineDay) = "day-${day.index}"

/** The line down the margin, the length of every day. */
private val LINE_WIDTH = 1.dp

/**
 * One week of the Path as a book (#1122, spec §Architecture; the web's `SpineWeek.tsx`):
 * the verso — masthead, opening, the persons of the week, the contents — then the recto:
 * the raked ground with every day of the week opened by its seam, oldest first, each
 * pebble a lit stone on the line, and the mouth where the week ends. Only the current week
 * can be empty; its recto is then the fresh-week invitation.
 *
 * One `LazyColumn`, every day item on the shared ground (the rake's phase runs over the
 * items' heights, so it crosses them without a seam) and on the line. The persons and the
 * contents follow the reading: the day crossing a line 35 % down the viewport turns ink in
 * both. A contents row scrolls its stone to the middle of the viewport and presses it
 * without opening (spec D8).
 *
 * The day geometry is the web's, hairline to content: 58 dp above a day's first pebble, 38
 * between pebbles, 34 below its last; a still day is 48 dp long with *still* 14 dp under
 * its hairline. The seam is [SEAM_LABEL_OVERHANG] taller than its hairline sits, for the
 * label hung above it, and that overhang comes off the previous day's bottom padding —
 * never off the day's own top — so the first day's seam follows the verso by [FEED_TOP]
 * minus the overhang, and the last day keeps its full 34 before the mouth. The day's top
 * padding rides on the seam item, so the label (which the seam never measures) overflows
 * into that padding and nothing drawn later covers it.
 *
 * @param today the local date, for which days exist and whether this is the current week.
 * @param paletteFor the pebble's emotion palette; null for a blank stone.
 * @param aspect the session's photo-ratio cache; null in previews and the lab.
 * @param onPreviousWeek absent on the first week there is; [onNextWeek] on the last.
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

    val listState = rememberLazyListState()
    val readingDay by rememberReadingDay(listState) { key -> key?.let(items.dayOfKey::get) }
    val dayRead = readingDay ?: week.days.firstOrNull()?.index

    // The ground's phase: each day item records its height, and the item after it starts
    // the rake where it left off. Draw-time reads, so a card settling to its photo's ratio
    // redraws the items under it and recomposes nothing.
    val tile = rememberRakeTile()
    val heights = remember(items) { mutableStateMapOf<Int, Int>() }

    // The contents → press: scroll the stone to the middle, then press it (never open).
    val pressRequests = remember { MutableSharedFlow<String>() }
    var goJob by remember { mutableStateOf<Job?>(null) }
    val go: (PathMoment) -> Unit = { moment ->
        val index = items.indexOf(moment.id)
        if (index >= 0) {
            goJob?.cancel()
            goJob =
                scope.launch {
                    val offset = stoneScrollOffset(listState, density, moment, items.placeInDay.getValue(moment.id))
                    if (reduceMotion) {
                        listState.scrollToItem(index, offset)
                    } else {
                        listState.animateScrollToItem(index, offset)
                        delay(StoneMotion.GO_PRESS_DELAY_MS)
                    }
                    pressRequests.emit(moment.id)
                }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = FEED_BOTTOM),
    ) {
        item(key = KEY_MASTHEAD) { WeekMasthead(words = words, onPreviousWeek = onPreviousWeek) }
        item(key = KEY_OPENING) { WeekOpening(words = words, pebbleCount = week.pebbleCount, peopleCount = persons.size) }
        if (persons.isNotEmpty()) {
            item(key = KEY_PERSONS) {
                PersonsOfWeek(persons = persons, weekStart = entry.weekStart, soulsById = soulsById, readingDay = dayRead)
            }
        }
        if (week.readingOrder.isNotEmpty()) {
            item(key = KEY_CONTENTS) { WeekContents(week = week, paletteFor = paletteFor, readingDay = dayRead, onGo = go) }
        }
        if (empty) {
            item(key = KEY_EMPTY) { SpineEmptyWeek(onCreatePebble = onCreatePebble) }
            return@LazyColumn
        }

        val lastDay = week.days.lastIndex
        week.days.forEachIndexed { position, day ->
            val first = position == 0
            val last = position == lastDay
            item(key = seamKey(day)) {
                val seam =
                    Modifier
                        .onGround(items, seamKey(day), tile, heights, colors.line, topInset = if (first) FEED_TOP else 0.dp)
                        .padding(top = if (first) FEED_TOP - SEAM_LABEL_OVERHANG else 0.dp)
                if (day.still) {
                    // Box top to the next box top, or to the mouth when nothing follows.
                    DaySeam(day = day, modifier = seam.heightIn(min = STILL_MIN_HEIGHT + if (last) SEAM_LABEL_OVERHANG else 0.dp))
                } else {
                    DaySeam(day = day, modifier = seam.padding(bottom = DAY_PAD_TOP - SEAM_HEIGHT))
                }
            }
            items(day.moments, key = { it.id }) { moment ->
                val place = items.placeInDay.getValue(moment.id)
                val lastOfDay = place == day.moments.lastIndex
                SpinePebble(
                    moment = moment,
                    palette = paletteFor(moment),
                    soulsById = soulsById,
                    personsOrder = personsOrder,
                    aspect = aspect,
                    pressRequests = pressRequests,
                    onOpen = onOpen,
                    onRequestDelete = onRequestDelete,
                    modifier =
                        Modifier
                            .onGround(items, moment.id, tile, heights, colors.line)
                            .padding(
                                top = pebbleTopPadding(place),
                                bottom =
                                    when {
                                        !lastOfDay -> 0.dp
                                        last -> DAY_PAD_BOTTOM
                                        else -> DAY_PAD_BOTTOM - SEAM_LABEL_OVERHANG
                                    },
                            ),
                    ratioOverride = moment.firstSnapPath?.let { prefix -> ratioOverride?.invoke(prefix) },
                )
            }
        }
        item(key = KEY_MOUTH) {
            WeekMouth(
                words = words,
                current = current,
                onNextWeek = onNextWeek,
                // The mouth draws its own last run of the line.
                modifier = Modifier.onGround(items, KEY_MOUTH, tile, heights, line = null),
            )
        }
    }
}

/** Between two pebbles of a day; the day's first follows its seam's padding instead. */
private fun pebbleTopPadding(placeInDay: Int): Dp = if (placeInDay == 0) 0.dp else PEBBLE_GAP

/**
 * A day item: records its height for the items below, draws the rake at the phase the
 * items above left, and the line down the margin, both from [topInset].
 */
private fun Modifier.onGround(
    items: WeekItems,
    key: Any,
    tile: Bitmap,
    heights: SnapshotStateMap<Int, Int>,
    line: Color?,
    topInset: Dp = 0.dp,
): Modifier {
    val ground = items.groundIndex.getValue(key)
    return onSizeChanged { heights[ground] = it.height }
        .spineGround(tile, topInset) { (0 until ground).sumOf { heights[it] ?: 0 }.toFloat() }
        .then(
            if (line == null) {
                Modifier
            } else {
                Modifier.drawBehind {
                    val x = SPINE_X.toPx()
                    drawLine(line, Offset(x, topInset.toPx()), Offset(x, size.height), LINE_WIDTH.toPx())
                }
            },
        )
}

/**
 * The `scrollOffset` that puts [moment]'s stone in the middle of the viewport: the item's
 * top lands half a viewport up from the stone's centre, which sits its top padding plus the
 * anchor into the item. Negative, as `LazyListState` counts an offset from the item's top.
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

/** Where everything sits in the list, computed once per week. */
private class WeekItems(
    week: SpineWeek,
    hasPersons: Boolean,
    empty: Boolean,
) {
    private val keys = ArrayList<Any>()

    /** The day an item belongs to — its seam and its moments — for the reading day. */
    val dayOfKey = HashMap<Any, Int>()

    /** The items on the ground, in order: the rake's phase accumulates over them. */
    val groundIndex = HashMap<Any, Int>()

    /** A moment's place in its day: 0 is the day's first pebble. */
    val placeInDay = HashMap<String, Int>()

    init {
        keys += KEY_MASTHEAD
        keys += KEY_OPENING
        if (hasPersons) keys += KEY_PERSONS
        if (week.readingOrder.isNotEmpty()) keys += KEY_CONTENTS
        if (empty) {
            keys += KEY_EMPTY
        } else {
            for (day in week.days) {
                val seam = seamKey(day)
                keys += seam
                dayOfKey[seam] = day.index
                groundIndex[seam] = groundIndex.size
                day.moments.forEachIndexed { place, moment ->
                    keys += moment.id
                    dayOfKey[moment.id] = day.index
                    groundIndex[moment.id] = groundIndex.size
                    placeInDay[moment.id] = place
                }
            }
            keys += KEY_MOUTH
            groundIndex[KEY_MOUTH] = groundIndex.size
        }
    }

    /** The item's index in the list, or −1. */
    fun indexOf(key: Any): Int = keys.indexOf(key)
}

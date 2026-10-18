package app.pbbls.android.features.path.spine

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import app.pbbls.android.core.designsystem.rememberReduceMotion
import kotlin.math.roundToInt

/** The reading line, as a fraction of the viewport's height: the web's `rootMargin: -35% …`. */
private const val READING_LINE = 0.35f

/** The web's `transition-colors duration-300` on the letter and the day that turn ink. */
private const val READING_INK_MS = 300

/**
 * The day being read (#1122, spec §Reading day; the web's `useReadingDay`): of the
 * visible items that belong to a day, in list order, the day of the last one at or above
 * [lineY]; null while none has reached it. A day's seam and its pebbles all count, so a
 * day whose seam has scrolled off the top is still read through the pebble under the line.
 *
 * Inline, so the scroll-time caller walks `visibleItemsInfo` without building a list.
 */
internal inline fun <T> readingDay(
    items: List<T>,
    lineY: Int,
    day: (T) -> Int?,
    offset: (T) -> Int,
): Int? {
    var read: Int? = null
    for (item in items) {
        val itemDay = day(item) ?: continue
        if (offset(item) <= lineY) read = itemDay else break
    }
    return read
}

/** [readingDay] over `(day index, offset in px)` pairs: the test's shape. */
internal fun readingDay(
    dayItems: List<Pair<Int, Int>>,
    lineY: Int,
): Int? = readingDay(dayItems, lineY, { it.first }, { it.second })

/**
 * [readingDay] over a `LazyListState`, derived so the week recomposes only when the day
 * changes, not on every scroll frame. Before the first day crosses the line it is null;
 * the web reads the first day then, so a caller wanting the same falls back to it.
 *
 * @param dayOfKey the day index an item key belongs to — a day's seam *and* its moments —
 *   or null for the opening, the mouth and the rest.
 */
@Composable
fun rememberReadingDay(
    listState: LazyListState,
    dayOfKey: (Any?) -> Int?,
): State<Int?> {
    val dayOf by rememberUpdatedState(dayOfKey)
    return remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            // From the viewport's own start, so a top content padding never moves the line.
            val lineY = info.viewportStartOffset + (info.viewportSize.height * READING_LINE).roundToInt()
            readingDay(info.visibleItemsInfo, lineY, { dayOf(it.key) }, { it.offset })
        }
    }
}

/** The ink a letter or a day label takes as the reading reaches it, eased as the web's, or snapped under reduced motion. */
@Composable
internal fun readingInk(
    here: Boolean,
    on: Color,
    off: Color,
): Color {
    val reduceMotion = rememberReduceMotion()
    val ink by
        animateColorAsState(
            targetValue = if (here) on else off,
            animationSpec = if (reduceMotion) snap() else tween(READING_INK_MS),
            label = "readingInk",
        )
    return ink
}

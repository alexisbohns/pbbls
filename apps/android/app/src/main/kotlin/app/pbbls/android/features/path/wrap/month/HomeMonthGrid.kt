package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.data.LocalEmotionPaletteService
import app.pbbls.android.core.data.LocalReferenceDataService
import app.pbbls.android.core.model.EmotionFamilies
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.features.path.wrap.WrapLookups
import java.time.LocalDate

/**
 * The card's room the grid is given: 34 (the date) + 8 + 7 × 28 (the days) + 8 + 64 (the cairn), so a
 * day's cell is 28 dp, the mock's rows (#1383 D8).
 */
private val HOME_ROOM = 310.dp

/**
 * The month's weeks at the top of Home (#1383, spec D8): the month card's own [WeeksColumns], handed to
 * Home through `HomeMonthSlot` by the entry provider. It has no intro host, so every reveal reads settled
 * (`currentIntroReveal()` falls back to [app.pbbls.android.features.path.wrap.intro.IntroReveal.settled]).
 * Each column is one button saying its week; [onWeek] gets its Monday.
 *
 * The ids resolve as the Path resolves them (`PathScreen`), from the ambient palettes and reference data,
 * minus the feed's moments and souls: Home holds no feed, and the columns only ink their pebbles by emotion.
 */
@Composable
fun HomeMonthGrid(
    wrap: MonthWrap,
    onWeek: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palettes = LocalEmotionPaletteService.current
    val references = LocalReferenceDataService.current
    val lookups =
        remember(palettes.byEmotionId, references.domains) {
            val families = EmotionFamilies.of(palettes.byEmotionId.values).associateBy { it.slug }
            val domains = references.domains.associateBy { it.id }
            WrapLookups(
                moment = { null },
                soul = { null },
                family = families::get,
                domain = domains::get,
                paletteFor = { moment -> moment.pebble?.emotion?.let { palettes.palette(it.id) } },
                emotion = palettes.byEmotionId::get,
            )
        }
    HomeMonthGrid(wrap, lookups, onWeek, modifier)
}

/** [HomeMonthGrid] over given [lookups]: what a preview renders, with its fixture's palettes. */
@Composable
internal fun HomeMonthGrid(
    wrap: MonthWrap,
    lookups: WrapLookups,
    onWeek: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        WeeksColumns(wrap = wrap, lookups = lookups, room = HOME_ROOM, onWeek = onWeek)
    }
}

package app.pbbls.android.features.path.wrap.month

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.data.LocalEmotionPaletteService
import app.pbbls.android.core.data.LocalReferenceDataService
import app.pbbls.android.core.model.EmotionFamilies
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.intro.IntroLedger
import app.pbbls.android.features.path.wrap.intro.rememberIntroVibrator
import app.pbbls.android.features.path.wrap.week.rememberWrapCopy
import java.time.LocalDate

/**
 * The month intros Home's wrap sheet has played this launch (#1383 D12; iOS `HomeModel.introLedger`): a month
 * unfolded on Home plays its deck once, then opens settled, until the process ends. Its own ledger, not the
 * Path's (`PathViewModel.introLedger`), as on iOS. Main thread only, as every ledger.
 */
private val homeIntros = IntroLedger()

/**
 * A revealed month in Home's wrap sheet (#1383 D12), handed to Home through `HomeWrapSlot` by the entry provider:
 * the Path's [MonthPage], its intro once per launch ([homeIntros]), focused while the sheet is up. Milestones are
 * inert (Home holds no feed, so no moment to open), and [onWeek] gets a tapped week's Monday.
 *
 * The ids resolve from the ambient palettes and reference data, as [HomeMonthGrid]'s, plus the souls, so the
 * persons section names them (iOS `HomeLookups`).
 */
@Composable
fun HomeWrapPage(
    wrap: MonthWrap,
    dayView: DayView,
    onDayView: (DayView) -> Unit,
    hapticsOn: () -> Boolean,
    onWeek: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palettes = LocalEmotionPaletteService.current
    val references = LocalReferenceDataService.current
    val lookups =
        remember(palettes.byEmotionId, references.domains, references.souls) {
            val families = EmotionFamilies.of(palettes.byEmotionId.values).associateBy { it.slug }
            val domains = references.domains.associateBy { it.id }
            val souls = references.souls.associateBy { it.id }
            WrapLookups(
                moment = { null },
                soul = souls::get,
                family = families::get,
                domain = domains::get,
                paletteFor = { moment -> moment.pebble?.emotion?.let { palettes.palette(it.id) } },
                emotion = palettes.byEmotionId::get,
            )
        }
    val haptics = rememberIntroVibrator(enabled = hapticsOn)
    val intro = remember(haptics) { MonthIntro(homeIntros, haptics) }
    MonthPage(
        wrap = wrap,
        lookups = lookups,
        dayView = dayView,
        onDayView = onDayView,
        onWeek = onWeek,
        onOpen = {},
        focused = true,
        modifier = modifier,
        intro = intro,
    )
}

/**
 * A folded month's line in Home's wrap sheet (#1383 D12; iOS `WrapCopy.line(…, live: false, hasMoments: true)`):
 * "Twenty-three pebbles, five people." Through `HomeWrapLineSlot`, as [HomeWrapPage].
 */
@Composable
fun homeWrapLine(
    pebbles: Int,
    people: Int,
): String = rememberWrapCopy().line(pebbles = pebbles, people = people, live = false, hasMoments = true)

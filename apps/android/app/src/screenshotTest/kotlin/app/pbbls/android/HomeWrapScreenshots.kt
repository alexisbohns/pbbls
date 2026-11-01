package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.home.HomeWrapMonth
import app.pbbls.android.core.model.wrap.CoverDay
import app.pbbls.android.core.ui.HomeWrapLineSlot
import app.pbbls.android.core.ui.HomeWrapSlot
import app.pbbls.android.features.home.HomeSection
import app.pbbls.android.features.home.wraps.HomeWrapContent
import app.pbbls.android.features.home.wraps.HomeWrapUiState
import app.pbbls.android.features.home.wraps.Unfold
import app.pbbls.android.features.path.wrap.month.MonthPage
import app.pbbls.android.features.path.wrap.month.homeWrapLine
import com.android.tools.screenshot.PreviewTest
import java.time.Instant
import java.time.YearMonth

/**
 * Home's months for the previews (#1383 D12), built in Kotlin: the screenshot renderer does not load the test
 * source set's `month-full.json`. October is the fixture's own month and cover ([SpineFixtureWeek.monthWrap]);
 * September carries a fuller ring, August is folded.
 */
internal object HomeWrapFixtures {
    private val revealedAt: Instant = Instant.parse("2026-11-01T09:00:00Z")

    val october: HomeWrapMonth by lazy {
        HomeWrapMonth(YearMonth.of(2026, 10), count = 23, revealedAt = revealedAt, cover = SpineFixtureWeek.monthWrap.cover)
    }

    val september: HomeWrapMonth by lazy {
        val days = listOf(0, 2, 3, 5, 6, 9, 11, 12, 13, 16, 18, 20, 21, 24, 26, 27, 29)
        val cover = requireNotNull(SpineFixtureWeek.monthWrap.cover).copy(days = days.map { CoverDay(it, emptyList()) })
        HomeWrapMonth(YearMonth.of(2026, 9), count = 41, revealedAt = revealedAt, cover = cover)
    }

    val august: HomeWrapMonth = HomeWrapMonth(YearMonth.of(2026, 8), count = 12, revealedAt = null, cover = null)

    val three: List<HomeWrapMonth> by lazy { listOf(october, september, august) }
}

/** The entry provider's fillers, over the fixture's lookups and with no intro (the settled page). */
private val previewWrap: HomeWrapSlot = { wrap, dayView, onDayView, _, onWeek, modifier ->
    MonthPage(
        wrap = wrap,
        lookups = SpineFixtureWeek.lookups,
        dayView = dayView,
        onDayView = onDayView,
        onWeek = onWeek,
        onOpen = {},
        focused = true,
        modifier = modifier,
        zone = SpineFixtureWeek.zone,
    )
}

private val previewLine: HomeWrapLineSlot = { pebbles, people -> homeWrapLine(pebbles, people) }

/** The sheet's body at a phone's size, as the docked sheet hosts it. */
@Composable
private fun SheetPreview(
    month: YearMonth,
    state: HomeWrapUiState,
) {
    PebblesTheme {
        Surface {
            Box(Modifier.width(390.dp).height(800.dp)) {
                HomeWrapContent(
                    month = month,
                    state = state,
                    wrapSlot = previewWrap,
                    wrapLine = previewLine,
                    dayView = DayView.CIRCLE,
                    onDayView = {},
                    hapticsOn = { false },
                    onUnfold = {},
                    onRetry = {},
                    onWeek = {},
                    onDone = {},
                )
            }
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 800)
@PreviewLargeFontTall
@PreviewFrench
@Composable
fun HomeWrapFolded() {
    SheetPreview(HomeWrapFixtures.october.month, HomeWrapUiState.Folded(HomeWrapFixtures.october.copy(revealedAt = null)))
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 800, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun HomeWrapFoldedFailedNight() {
    SheetPreview(HomeWrapFixtures.october.month, HomeWrapUiState.Folded(HomeWrapFixtures.october.copy(revealedAt = null), Unfold.FAILED))
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 800)
@PreviewFrench
@Composable
fun HomeWrapRevealed() {
    SheetPreview(
        HomeWrapFixtures.october.month,
        HomeWrapUiState.Revealed(HomeWrapFixtures.october, HomeSection.Ready(SpineFixtureWeek.monthWrap)),
    )
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 800, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun HomeWrapRevealedNight() {
    SheetPreview(
        HomeWrapFixtures.october.month,
        HomeWrapUiState.Revealed(HomeWrapFixtures.october, HomeSection.Ready(SpineFixtureWeek.monthWrap)),
    )
}

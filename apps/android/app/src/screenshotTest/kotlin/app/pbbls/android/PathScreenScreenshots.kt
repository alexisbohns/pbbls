package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.readableWidth
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.WeekRollEntry
import app.pbbls.android.features.path.PathContent
import com.android.tools.screenshot.PreviewTest
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Full-screen previews of the read-only Path timeline (#531): the populated
 * current week (roll + header + rows + temporary sign-out) and the fresh-week
 * empty state, and a week mixing completed and uncompleted moments — light and
 * dark. Driven through the stateless [PathContent]
 * with fixture data (no services).
 */
private val screenPalette: EmotionPalette =
    requireNotNull(
        EmotionPalette.fromHex(
            primaryHex = "#7B5E99FF",
            secondaryHex = "#AE91CCFF",
            lightHex = "#F2EFF5FF",
            surfaceHex = "#7B5E991A",
            darkHex = "#2A2138FF",
            shadedHex = "#4A3A5CFF",
        ),
    )

private fun moment(
    name: String,
    happenedAt: String,
    valence: Valence,
): PathMoment =
    MomentPreviewFixtures.pathMoment(
        id = name,
        name = name,
        happenedAt = OffsetDateTime.parse(happenedAt),
        pebble = MomentPreviewFixtures.pathPebble(valence = valence),
    )

// Focused week Monday 2026-07-06; "today" Saturday 2026-07-11. Both internal
// so the Path list-detail renders show this exact timeline (#940).
internal val pathPreviewToday: LocalDate = LocalDate.of(2026, 7, 11)

internal val populatedEntries: List<WeekRollEntry> =
    listOf(
        WeekRollEntry(
            weekStart = LocalDate.of(2026, 6, 22),
            moments =
                listOf(
                    moment("Long walk", "2026-06-23T09:10:00+00:00", Valence.NEUTRAL_SMALL),
                ),
        ),
        WeekRollEntry(
            weekStart = LocalDate.of(2026, 7, 6),
            moments =
                listOf(
                    moment("Concert with Lea", "2026-07-10T21:30:00+00:00", Valence.HIGHLIGHT_LARGE),
                    moment("Hard conversation", "2026-07-08T18:00:00+00:00", Valence.LOWLIGHT_MEDIUM),
                    moment("Morning coffee", "2026-07-06T08:15:00+00:00", Valence.HIGHLIGHT_SMALL),
                ),
        ),
    )

private val emptyWeekEntries: List<WeekRollEntry> =
    listOf(WeekRollEntry(weekStart = LocalDate.of(2026, 7, 6), moments = emptyList()))

@Composable
private fun ScreenPreview(
    entries: List<WeekRollEntry>,
    paletteFor: (PathMoment) -> EmotionPalette? = { screenPalette },
) {
    val focused = LocalDate.of(2026, 7, 6)
    PathContent(
        entries = entries,
        initialWeekStart = focused,
        focusedWeekStart = focused,
        today = pathPreviewToday,
        onFocusChange = {},
        paletteFor = paletteFor,
        // Mirrors PathScreen: the timeline is the readable column (#855), a
        // no-op at phone widths.
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .readableWidth(),
    )
}

@PreviewTest
@Preview(showBackground = true)
@PreviewWide
@PreviewLargeFont
@PreviewFrench
@Composable
fun PathScreenPopulatedLight() {
    PebblesTheme { ScreenPreview(populatedEntries) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PathScreenPopulatedDark() {
    PebblesTheme { ScreenPreview(populatedEntries) }
}

@PreviewTest
@Preview(showBackground = true)
@PreviewFrench
@Composable
fun PathScreenFreshWeekLight() {
    PebblesTheme { ScreenPreview(emptyWeekEntries) }
}

/**
 * A week mixing a completed pebble, a photo-only capture ("Untitled", R3) and a
 * named moment without its pebble: both uncompleted ones draw the blank stone
 * (R4), with no palette, as `PathScreen` resolves it.
 */
private val mixedWeekEntries: List<WeekRollEntry> =
    listOf(
        WeekRollEntry(
            weekStart = LocalDate.of(2026, 7, 6),
            moments =
                listOf(
                    moment("Concert with Lea", "2026-07-10T21:30:00+00:00", Valence.HIGHLIGHT_LARGE),
                    MomentPreviewFixtures.pathMoment(
                        id = "photo-only",
                        name = null,
                        happenedAt = OffsetDateTime.parse("2026-07-09T12:05:00+00:00"),
                        firstSnapPath = "user-1/snap-1",
                        pebble = null,
                    ),
                    MomentPreviewFixtures.pathMoment(
                        id = "named-bare",
                        name = "Bus stop sunset",
                        happenedAt = OffsetDateTime.parse("2026-07-07T19:40:00+00:00"),
                        pebble = null,
                    ),
                ),
        ),
    )

@PreviewTest
@Preview(showBackground = true)
@PreviewFrench
@Composable
fun PathScreen_MixedWeek() {
    PebblesTheme { ScreenPreview(mixedWeekEntries, paletteFor = { if (it.isCompleted) screenPalette else null }) }
}

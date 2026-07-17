package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.features.path.PathContent
import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.EmotionRef
import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.features.path.models.WeekRollEntry
import app.pbbls.android.theme.PebblesTheme
import com.android.tools.screenshot.PreviewTest
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Full-screen previews of the read-only Path timeline (#531): the populated
 * current week (roll + header + rows + temporary sign-out) and the fresh-week
 * empty state — light and dark. Driven through the stateless [PathContent]
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

private fun pebble(
    name: String,
    happenedAt: String,
    intensity: Int,
    positiveness: Int,
    renderSvg: String?,
): Pebble =
    Pebble(
        id = name,
        name = name,
        happenedAt = OffsetDateTime.parse(happenedAt),
        createdAt = OffsetDateTime.parse(happenedAt),
        intensity = intensity,
        positiveness = positiveness,
        renderSvg = renderSvg,
        emotion = EmotionRef(id = "e1", slug = "joyful", name = "Joyful"),
    )

// Focused week Monday 2026-07-06; "today" Saturday 2026-07-11.
private val today: LocalDate = LocalDate.of(2026, 7, 11)

private val populatedEntries: List<WeekRollEntry> =
    listOf(
        WeekRollEntry(
            weekStart = LocalDate.of(2026, 6, 22),
            pebbles =
                listOf(
                    pebble("Long walk", "2026-06-23T09:10:00+00:00", 1, 0, PebbleSvgFixtures.smallNeutral),
                ),
        ),
        WeekRollEntry(
            weekStart = LocalDate.of(2026, 7, 6),
            pebbles =
                listOf(
                    pebble("Concert with Lea", "2026-07-10T21:30:00+00:00", 3, 1, PebbleSvgFixtures.largeHighlight),
                    pebble("Hard conversation", "2026-07-08T18:00:00+00:00", 2, -1, PebbleSvgFixtures.mediumLowlight),
                    pebble("Morning coffee", "2026-07-06T08:15:00+00:00", 1, 1, PebbleSvgFixtures.smallHighlight),
                ),
        ),
    )

private val emptyWeekEntries: List<WeekRollEntry> =
    listOf(WeekRollEntry(weekStart = LocalDate.of(2026, 7, 6), pebbles = emptyList()))

@Composable
private fun ScreenPreview(entries: List<WeekRollEntry>) {
    val focused = LocalDate.of(2026, 7, 6)
    PathContent(
        entries = entries,
        initialWeekStart = focused,
        focusedWeekStart = focused,
        today = today,
        onFocusChange = {},
        paletteFor = { screenPalette },
        modifier = Modifier.fillMaxSize().background(PebblesTheme.colors.system.background),
    )
}

@PreviewTest
@Preview(showBackground = true)
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
@Composable
fun PathScreenFreshWeekLight() {
    PebblesTheme { ScreenPreview(emptyWeekEntries) }
}

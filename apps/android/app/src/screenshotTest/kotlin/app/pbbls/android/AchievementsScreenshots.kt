package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.features.profile.AchievementsScreen
import app.pbbls.android.features.profile.AchievementsUiState
import app.pbbls.android.services.AchievementRecord
import app.pbbls.android.theme.PebblesTheme
import com.android.tools.screenshot.PreviewTest
import java.time.OffsetDateTime

/**
 * The achievements screen in all three of its states (#849).
 *
 * **The screen itself is the subject here, not a gallery of its parts** — which
 * is new. Until this issue, `AchievementsScreen` read `LocalAchievementsService`
 * at the top and so could not be composed from a preview at all (that local's
 * default `error(...)`s, and the real service reaches `AppEnvironment`), leaving
 * this screen with no render coverage of any kind. The ViewModel split moved
 * every service read behind `hiltViewModel()` and left a stateless overload that
 * takes an `AchievementsUiState`, so the top bar, the spinner and the error
 * branch are now exactly as renderable as the grid.
 *
 * That is the pattern the rest of #849 repeats, and the reason the screenshot
 * gate gets meaningfully wider as it lands: `apps/android/CLAUDE.md` records
 * that a component gallery "does not catch a regression in the screen's own
 * scroll column or top bar", and this is what closes that gap.
 *
 * The families used below (`pebble_count`, `first_glyph`) are deliberate: the
 * `emotion_first` and `domain_first` copy resolves through
 * `LocalEmotionPaletteService`, which a preview cannot provide.
 */
private fun record(
    id: String,
    family: String,
    sortOrder: Int,
    threshold: Int? = null,
) = AchievementRecord(
    id = id,
    slug = id,
    family = family,
    threshold = threshold,
    sortOrder = sortOrder,
    karmaReward = 10,
    isActive = true,
)

private val contentState =
    AchievementsUiState.Content(
        catalog =
            listOf(
                record("first-pebble", family = "pebble_count", sortOrder = 1, threshold = 1),
                record("ten-pebbles", family = "pebble_count", sortOrder = 2, threshold = 10),
                record("first-glyph", family = "first_glyph", sortOrder = 3),
            ),
        // One unlocked, two locked — the grid's two visual treatments side by side.
        unlockedAt = mapOf("first-pebble" to OffsetDateTime.parse("2026-07-14T09:30:00Z")),
    )

@Composable
private fun AchievementsAt(state: AchievementsUiState) {
    PebblesTheme {
        AchievementsScreen(uiState = state, onBack = {}, onRetry = {})
    }
}

@PreviewTest
@Preview(showBackground = true)
@PreviewLargeFont
@PreviewFrench
@Composable
fun AchievementsContentLight() {
    AchievementsAt(contentState)
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun AchievementsContentDark() {
    AchievementsAt(contentState)
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun AchievementsLoadingLight() {
    AchievementsAt(AchievementsUiState.Loading)
}

/**
 * The branch nothing has ever rendered: a failed load is the state a user is
 * most likely to meet on a bad connection and the one most likely to clip its
 * own text, so it gets the large-font variant too.
 */
@PreviewTest
@Preview(showBackground = true)
@PreviewLargeFont
@Composable
fun AchievementsErrorLight() {
    AchievementsAt(AchievementsUiState.Error(R.string.achievements_load_error))
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun AchievementsErrorDark() {
    AchievementsAt(AchievementsUiState.Error(R.string.achievements_load_error))
}

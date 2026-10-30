package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.achievements.AchievementsPage
import app.pbbls.android.features.achievements.AchievementsScreen
import app.pbbls.android.features.achievements.AchievementsUiState
import com.android.tools.screenshot.PreviewTest

/**
 * The achievements page (#1307) in its three states. The screen itself is the subject, chrome
 * included, as `AchievementsScreenshots` was for the grid. Tall, so the whole list renders; the
 * sheets have their own set (`AchievementSheetsScreenshots`).
 */
@Composable
private fun PageAt(
    state: AchievementsUiState,
    pulling: Boolean = false,
) {
    PebblesTheme {
        AchievementsScreen(
            uiState = state,
            sheets = emptyList(),
            lookups = AchievementsFixture.lookups,
            onBack = {},
            onRetry = {},
            onOpenSheet = {},
            onCloseSheet = {},
            onOpenMoment = {},
            onPull = {},
            pulling = pulling,
        )
    }
}

private val content = AchievementsUiState.Content(AchievementsFixture.page)

/** The six firsts whole and offered: Explorer's band carries the ready mark in place of its hint. */
private val ready: AchievementsPage =
    AchievementsFixture.page.let { page ->
        page.copy(explorer = page.explorer?.let { it.copy(completion = it.completion?.copy(ready = true, pulledAt = null)) })
    }

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 2000)
@PreviewFrench
@Composable
fun AchievementsPageLight() {
    PageAt(content)
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 2000, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun AchievementsPageDark() {
    PageAt(content)
}

/**
 * The accessibility ceiling: every card's text grows, the figures keep their place. A tall viewport
 * of its own, not `@PreviewLargeFontTall` (720 dp), which would show only the header and Explorer.
 */
@PreviewTest
@Preview(name = "fs2", showBackground = true, fontScale = 2f, widthDp = 390, heightDp = 3000)
@Composable
fun AchievementsPageLargeFont() {
    PageAt(content)
}

@PreviewTest
@Preview(showBackground = true, widthDp = 360, heightDp = 2000)
@Composable
fun AchievementsPageNarrow() {
    PageAt(content)
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
fun AchievementsPageReady() {
    PageAt(AchievementsUiState.Content(ready))
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
fun AchievementsPagePulling() {
    PageAt(AchievementsUiState.Content(ready), pulling = true)
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun AchievementsPageLoading() {
    PageAt(AchievementsUiState.Loading)
}

@PreviewTest
@Preview(showBackground = true)
@PreviewLargeFont
@PreviewFrench
@Composable
fun AchievementsPageErrorLight() {
    PageAt(AchievementsUiState.Error(R.string.achievements_error_body))
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun AchievementsPageErrorDark() {
    PageAt(AchievementsUiState.Error(R.string.achievements_error_body))
}

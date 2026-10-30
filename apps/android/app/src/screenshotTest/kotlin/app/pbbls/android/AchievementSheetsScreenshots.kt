package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.achievements.AchievementSheet
import app.pbbls.android.features.achievements.sheets.AchievementSheetContent
import com.android.tools.screenshot.PreviewTest

/**
 * The five sheets (#1307), unrolled on the stock as `final/sheets.html` shows them, in paper and
 * night. A `ModalBottomSheet` is a separate window layoutlib does not render, so the content is
 * drawn as the sheet holds it.
 */
@Composable
private fun Sheet(sheet: AchievementSheet) {
    Surface(color = PebblesTheme.spine.colors.stock) {
        AchievementSheetContent(
            sheet = sheet,
            page = AchievementsFixture.page,
            lookups = AchievementsFixture.lookups,
            onPush = {},
            onOpenMoment = {},
            onPull = {},
            // Scrolled, as the sheet holds it: a tall sheet (large font) lays out whole and the preview
            // shows its top, rather than squeezing the rows that do not fit.
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 34.dp),
        )
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 1900)
@PreviewFrench
@Composable
fun AchievementSheetExplorerLight() {
    PebblesTheme { Sheet(AchievementSheet.Explorer) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 1900, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun AchievementSheetExplorerDark() {
    PebblesTheme { Sheet(AchievementSheet.Explorer) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 2600)
@Composable
fun AchievementSheetEmotionsLight() {
    PebblesTheme { Sheet(AchievementSheet.Emotions) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 2600, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun AchievementSheetEmotionsDark() {
    PebblesTheme { Sheet(AchievementSheet.Emotions) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 1300)
@PreviewLargeFont
@Composable
fun AchievementSheetDomainsLight() {
    PebblesTheme { Sheet(AchievementSheet.Domains) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 1300, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun AchievementSheetDomainsDark() {
    PebblesTheme { Sheet(AchievementSheet.Domains) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 1500)
@PreviewFrench
@Composable
fun AchievementSheetPebblesLight() {
    PebblesTheme { Sheet(AchievementSheet.Pebbles) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 1500, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun AchievementSheetPebblesDark() {
    PebblesTheme { Sheet(AchievementSheet.Pebbles) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 1300)
@PreviewLargeFont
@Composable
fun AchievementSheetYearLight() {
    PebblesTheme { Sheet(AchievementSheet.Year(2026)) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 1300, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun AchievementSheetYearDark() {
    PebblesTheme { Sheet(AchievementSheet.Year(2026)) }
}

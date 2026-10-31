package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.EmotionFamilies
import app.pbbls.android.core.model.home.GuideIllustration
import app.pbbls.android.features.home.guides.GuideContent
import app.pbbls.android.features.home.guides.GuideIllustrationView
import app.pbbls.android.features.home.guides.GuideUiState
import com.android.tools.screenshot.PreviewTest

/**
 * The guide page (#1383 D13): the stones' reading (Paper, Night, French with its English body, 2 × font), the
 * emotions' with the seven families' palettes, the missing reading, and the nine pictures together.
 */
private val familyPalettes = EmotionFamilies.of(SpineFixtureWeek.emotionsById.values).map { it.palette }

@Composable
private fun GuidePreview(state: GuideUiState) {
    PebblesTheme {
        GuideContent(state = state, onBack = {}, familyPalettes = familyPalettes)
    }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(name = "fr", showBackground = true, heightDp = 720, locale = "fr")
@PreviewLargeFontTall
@Composable
fun GuideStonesPaper() {
    GuidePreview(GuideUiState.Found(GuideFixtures.bySlug("stones")))
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun GuideStonesNight() {
    GuidePreview(GuideUiState.Found(GuideFixtures.bySlug("stones")))
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Composable
fun GuideEmotionsPaper() {
    GuidePreview(GuideUiState.Found(GuideFixtures.bySlug("emotions")))
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(name = "fr", showBackground = true, heightDp = 720, locale = "fr")
@Composable
fun GuideMissingPaper() {
    GuidePreview(GuideUiState.Missing)
}

/** The nine 120 dp pictures, one under the other, to judge each against iOS's. */
@PreviewTest
@Preview(showBackground = true, heightDp = 1180)
@Preview(name = "night", showBackground = true, heightDp = 1180, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun GuidePictures() {
    PebblesTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm)) {
                GuideIllustration.entries.forEach {
                    GuideIllustrationView(kind = it, modifier = Modifier.fillMaxWidth(), familyPalettes = familyPalettes)
                }
            }
        }
    }
}

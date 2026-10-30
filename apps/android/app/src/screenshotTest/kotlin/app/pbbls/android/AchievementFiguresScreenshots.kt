package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.achievements.figures.CardLadder
import app.pbbls.android.features.achievements.figures.CardLadders
import app.pbbls.android.features.achievements.figures.ExplorerWheel
import app.pbbls.android.features.achievements.figures.Rosace
import app.pbbls.android.features.achievements.figures.ShardBand
import app.pbbls.android.features.achievements.figures.Skyline
import app.pbbls.android.features.achievements.figures.SkylineGeometry
import app.pbbls.android.features.achievements.figures.YearDisc
import app.pbbls.android.features.achievements.figures.domainBandItems
import app.pbbls.android.features.achievements.figures.skylineFill
import com.android.tools.screenshot.PreviewTest

/** The five organisms and the ladder (#1307) on the board's page, in paper and night. */
@Composable
private fun Figures() {
    val page = AchievementsFixture.page
    val lookups = AchievementsFixture.lookups
    Surface(color = PebblesTheme.spine.colors.stock) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ExplorerWheel(page.explorer!!, page.emotions, page.domains, lookups, description = "")
            Rosace(page.emotions!!, lookups, description = "", centre = "23" to "OF 38")
            ShardBand(domainBandItems(page.domains!!), description = "")
            Skyline(page.pebbles!!.skylineFill(), description = "")
            Skyline(page.glyphsCarved!!.skylineFill(), description = "", viewport = SkylineGeometry.MINI, width = 103.dp)
            YearDisc(page.years.single(), lookups, description = "")
            CardLadder(CardLadders.year(page.years.single()))
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 1500)
@Composable
fun AchievementFiguresLight() {
    PebblesTheme { Figures() }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 1500, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun AchievementFiguresDark() {
    PebblesTheme { Figures() }
}

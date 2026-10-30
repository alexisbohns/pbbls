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
import app.pbbls.android.core.model.achievements.AchievementMonth
import app.pbbls.android.core.model.achievements.AchievementsPage
import app.pbbls.android.core.model.achievements.FirstFiveItem
import app.pbbls.android.core.model.achievements.MonthState
import app.pbbls.android.core.model.achievements.SixFirst
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

/** The five organisms and the ladder (#1307) on [page], in paper and night. */
@Composable
private fun Figures(page: AchievementsPage = AchievementsFixture.page) {
    val lookups = AchievementsFixture.lookups
    Surface(color = PebblesTheme.spine.colors.stock) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ExplorerWheel(page.explorer!!, page.emotions, page.domains, lookups, description = "")
            Rosace(page.emotions!!, lookups, description = "", centre = page.emotions!!.count.toString() to "OF ${page.emotions!!.of}")
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
@Preview(name = "fs2-narrow", showBackground = true, widthDp = 320, heightDp = 1500, fontScale = 2f)
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

/**
 * What a client must survive, drawn (#1307): an emotion family this device does not know (ink),
 * a met seat whose pebble is gone (medium even), a kept month in an unknown family (ink, its rim
 * ring kept), a longer first five and a shorter six firsts than the board's, and a task and a
 * kind it does not know (ghosts in their places), with no 25th served (25 ghosts).
 */
private val tolerance: AchievementsPage =
    AchievementsFixture.page.let { page ->
        val explorer = page.explorer!!
        val emotions = page.emotions!!
        val year = page.years.single()
        page.copy(
            explorer =
                explorer.copy(
                    firstFive = explorer.firstFive + FirstFiveItem(null, done = true, on = null, subject = null, isNew = true),
                    sixFirsts = explorer.sixFirsts.take(3) + SixFirst(null, done = true, on = null, pebble = null, isNew = false),
                    twentyFive = null,
                ),
            emotions =
                emotions.copy(
                    seats =
                        emotions.seats.mapIndexed { i, s ->
                            when {
                                i < 9 -> s.copy(family = "awe")
                                s.met && i == 12 -> s.copy(pebble = null)
                                else -> s
                            }
                        },
                ),
            years =
                listOf(
                    year.copy(
                        months =
                            year.months.map { m: AchievementMonth ->
                                if (m.month == 8) m.copy(family = "awe", state = MonthState.KEPT) else m
                            },
                    ),
                ),
        )
    }

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 1500)
@Composable
fun AchievementFiguresTolerance() {
    PebblesTheme { Figures(tolerance) }
}

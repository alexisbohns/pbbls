package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.achievements.FirstStep
import app.pbbls.android.core.model.achievements.PebbleSnapshot
import app.pbbls.android.core.model.achievements.StoneKind
import app.pbbls.android.features.achievements.figures.FirstFiveMark
import app.pbbls.android.features.achievements.figures.MonthCounter
import app.pbbls.android.features.achievements.figures.PebbleSealMark
import app.pbbls.android.features.achievements.figures.SixFirstMark
import app.pbbls.android.features.achievements.figures.SkylineStage
import app.pbbls.android.features.achievements.figures.emotionSealWidth
import com.android.tools.screenshot.PreviewTest
import java.time.Instant

/**
 * The register rows' marks (#1307): each first-five task and six-firsts kind done, not yet and
 * unknown (a ghost), a pebble's seal in its palette and in ink, the skyline's stages listed, rising
 * and empty, and the year's counters on the rim, inside, quiet and to come.
 */
@Composable
private fun Marks() {
    val lookups = AchievementsFixture.lookups
    val year = AchievementsFixture.page.years.single()
    Surface(color = PebblesTheme.spine.colors.stock) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FirstStep.entries.forEach { FirstFiveMark(it, done = true) }
                FirstFiveMark(FirstStep.SOUL, done = false)
                FirstFiveMark(step = null, done = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StoneKind.entries.forEach { SixFirstMark(it, done = true) }
                SixFirstMark(StoneKind.LARGE, done = false)
                SixFirstMark(kind = null, done = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Valence.LOWLIGHT_SMALL, Valence.NEUTRAL_MEDIUM, Valence.HIGHLIGHT_LARGE).forEach { valence ->
                    val known = PebbleSnapshot("m", null, Instant.EPOCH, "50a68890-f6bb-e67a-6655-e84ff0b71f49", valence)
                    PebbleSealMark(known, lookups, emotionSealWidth(valence.intensity) * 2)
                    PebbleSealMark(known.copy(emotionId = "emotion-unknown"), lookups, emotionSealWidth(valence.intensity) * 2)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0 until 8).forEach { stage ->
                    SkylineStage(
                        stage,
                        fill =
                            if (stage < 2) {
                                1f
                            } else if (stage == 2) {
                                0.4f
                            } else {
                                0f
                            },
                        done = stage < 2,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                year.months.forEach { MonthCounter(it, lookups) }
            }
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390)
@Composable
fun AchievementMarksLight() {
    PebblesTheme { Marks() }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun AchievementMarksDark() {
    PebblesTheme { Marks() }
}

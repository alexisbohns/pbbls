package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.path.spine.SpineGround
import app.pbbls.android.features.path.spine.SpineLayout
import app.pbbls.android.features.path.spine.logic.SpineDay
import app.pbbls.android.features.path.spine.logic.SpineWeekBuilder
import com.android.tools.screenshot.PreviewTest

/**
 * The Spine's feed (#1122 Part 4): four days of the fixture week on the raked
 * ground at phone width, Paper and Night: Monday (a card and an entry),
 * Wednesday (an entry, a card and the named moment that is not a pebble yet),
 * Friday (still) and Sunday (an entry, Bruno's glyph-less pebble and the
 * untitled photo that is not a pebble yet); and the fourteen-person picnic
 * alone, for the people line's overflow. The day layout is `SpineLabWeek`'s
 * (debug sources, shared with the Spine lab): no network, ratios primed.
 */
private val PHONE_WIDTH = 390.dp

private val previewDays: List<SpineDay> by lazy {
    val week = SpineWeekBuilder.build(SpineFixtureWeek.entry, SpineFixtureWeek.zone, SpineFixtureWeek.today)
    listOf(week.days[0], week.days[2], week.days[4], week.days[6])
}

@Composable
private fun OnGround(content: @Composable () -> Unit) {
    Surface(color = PebblesTheme.spine.colors.ground) {
        Box(Modifier.width(PHONE_WIDTH)) {
            SpineGround(Modifier.matchParentSize())
            Column(Modifier.padding(top = SPINE_LAB_LABEL_ROOM)) { content() }
        }
    }
}

@Composable
private fun SpineFeedDays() {
    OnGround { previewDays.forEach { day -> FixtureDay(day) } }
}

@Composable
private fun SpineFeedPicnic() {
    OnGround {
        FixturePebble(SpineFixtureWeek.picnic, Modifier.padding(top = SpineLayout.DAY_PAD_TOP, bottom = SpineLayout.DAY_PAD_BOTTOM))
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun SpineFeedLight() {
    PebblesTheme { SpineFeedDays() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun SpineFeedDark() {
    PebblesTheme { SpineFeedDays() }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun SpineFeedPicnicLight() {
    PebblesTheme { SpineFeedPicnic() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun SpineFeedPicnicDark() {
    PebblesTheme { SpineFeedPicnic() }
}

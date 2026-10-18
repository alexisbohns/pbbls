package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.features.path.spine.DaySeam
import app.pbbls.android.features.path.spine.SpineGround
import app.pbbls.android.features.path.spine.SpineLayout
import app.pbbls.android.features.path.spine.SpinePebble
import app.pbbls.android.features.path.spine.logic.SpineDay
import app.pbbls.android.features.path.spine.logic.SpineWeekBuilder
import com.android.tools.screenshot.PreviewTest
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * The Spine's feed (#1122 Part 4): three days of the fixture week on the raked
 * ground at phone width, Paper and Night: Monday (a card and an entry),
 * Wednesday (an entry, a card and the named moment that is not a pebble yet)
 * and Friday (still); and the fourteen-person picnic alone, for the people
 * line's overflow. No network: the ratios are primed and the photo URLs
 * withheld, so the cards show their tinted fields.
 */
private val PHONE_WIDTH = 390.dp

/** Room above the first seam for its hung label (`SpineLayout.LABEL_TOP`). */
private val LABEL_ROOM = 16.dp
private val HAIRLINE = 1.dp

private val previewDays: List<SpineDay> by lazy {
    val week = SpineWeekBuilder.build(SpineFixtureWeek.entry, SpineFixtureWeek.zone, SpineFixtureWeek.today)
    listOf(week.days[0], week.days[2], week.days[4])
}

@Composable
private fun FixturePebble(
    moment: PathMoment,
    modifier: Modifier = Modifier,
) {
    val requests = remember { MutableSharedFlow<String>() }
    SpinePebble(
        moment = moment,
        palette = SpineFixtureWeek.palettes(moment),
        soulsById = SpineFixtureWeek.soulsById,
        personsOrder = SpineFixtureWeek.personsOrder,
        aspect = null,
        pressRequests = requests,
        onOpen = {},
        onRequestDelete = {},
        modifier = modifier,
        ratioOverride = moment.firstSnapPath?.let { SpineFixtureWeek.ratios[it] },
        photoOverride = null to null,
    )
}

/** One day as the week lays it out: the seam, then the pebbles 58 dp below it, 38 dp apart. */
@Composable
private fun FixtureDay(day: SpineDay) {
    val line = PebblesTheme.spine.colors.line
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val x = SpineLayout.SPINE_X.toPx()
                drawLine(line, Offset(x, 0f), Offset(x, size.height), HAIRLINE.toPx())
            },
    ) {
        DaySeam(day = day)
        if (!day.still) {
            Column(
                Modifier.padding(top = SpineLayout.DAY_PAD_TOP - SpineLayout.SEAM_HEIGHT, bottom = SpineLayout.DAY_PAD_BOTTOM),
                verticalArrangement = Arrangement.spacedBy(SpineLayout.PEBBLE_GAP),
            ) {
                day.moments.forEach { moment -> FixturePebble(moment) }
            }
        } else {
            Spacer(Modifier.height(SpineLayout.DAY_PAD_BOTTOM))
        }
    }
}

@Composable
private fun OnGround(content: @Composable () -> Unit) {
    Surface(color = PebblesTheme.spine.colors.ground) {
        Box(Modifier.width(PHONE_WIDTH)) {
            SpineGround(Modifier.matchParentSize())
            Column(Modifier.padding(top = LABEL_ROOM)) { content() }
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

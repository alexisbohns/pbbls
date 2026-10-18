package app.pbbls.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.features.path.spine.DaySeam
import app.pbbls.android.features.path.spine.SpineGround
import app.pbbls.android.features.path.spine.SpineLayout
import app.pbbls.android.features.path.spine.SpinePebble
import app.pbbls.android.features.path.spine.logic.SpineDay
import app.pbbls.android.features.path.spine.logic.SpineWeekBuilder
import kotlinx.coroutines.flow.MutableSharedFlow

/*
 * The fixture week laid out as the Spine will lay it out (#1122 Part 4), for the
 * feed previews and the Spine lab: each day a seam with its pebbles 58 dp below
 * it and 38 dp apart, a hairline at `SPINE_X`, the raked ground behind. Part 5's
 * `SpineWeek` owns the real layout; this stands in until then. No network: the
 * ratios are primed and the photo URLs withheld, so the cards show their fields.
 */

/** Room above the first seam for its hung label (`SpineLayout.LABEL_TOP`). */
internal val SPINE_LAB_LABEL_ROOM = 16.dp
private val HAIRLINE = 1.dp

/** One fixture pebble: no cache, no photo, the ratio primed. */
@Composable
internal fun FixturePebble(
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
internal fun FixtureDay(day: SpineDay) {
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

/** The whole fixture week, scrolling, on the ground: the lab's Week tab. */
@Composable
internal fun SpineLabWeek(modifier: Modifier = Modifier) {
    val days =
        remember {
            SpineWeekBuilder.build(SpineFixtureWeek.entry, SpineFixtureWeek.zone, SpineFixtureWeek.today).days
        }
    Box(modifier.fillMaxSize()) {
        SpineGround(Modifier.fillMaxSize())
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = SPINE_LAB_LABEL_ROOM, bottom = 48.dp),
        ) {
            items(days, key = { it.index }) { day -> FixtureDay(day) }
        }
    }
}

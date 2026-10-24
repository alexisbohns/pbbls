package app.pbbls.android

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.features.path.spine.SpinePebble
import app.pbbls.android.features.path.spine.SpineWeek
import app.pbbls.android.features.path.spine.StonePressRequests
import app.pbbls.android.features.path.spine.WeekNeighbour
import app.pbbls.android.features.path.wrap.month.MonthPage

/*
 * The fixture week on the Spine (#1122), for the Spine lab's Week tab and the
 * previews. No network: the ratios are primed and the photo URLs withheld, so the
 * cards show their fields. The mouth's week link is live so it renders; it goes nowhere.
 */

/** The whole fixture week as `SpineWeek` lays it out: the lab's Week tab. */
@Composable
internal fun SpineLabWeek(modifier: Modifier = Modifier) {
    SpineWeek(
        entry = SpineFixtureWeek.entry,
        today = SpineFixtureWeek.today,
        soulsById = SpineFixtureWeek.soulsById,
        paletteFor = SpineFixtureWeek.palettes,
        aspect = null,
        onOpen = { _, _ -> },
        onRequestDelete = {},
        // The settled week's wrap, so the lab shows every tile (the fixture's Sunday would be live).
        wrap = SpineFixtureWeek.wrap(live = false),
        lookups = SpineFixtureWeek.lookups,
        nextWeek = WeekNeighbour(SpineFixtureWeek.nextNumber) {},
        onCreatePebble = {},
        modifier = modifier,
        zone = SpineFixtureWeek.zone,
        ratioOverride = SpineFixtureWeek.ratios::get,
    )
}

/** The fixture's October page (#1207): the lab's Month tab, the day view switchable but not saved. */
@Composable
internal fun SpineLabMonth(modifier: Modifier = Modifier) {
    var view by remember { mutableStateOf(DayView.CIRCLE) }
    Surface(color = PebblesTheme.spine.colors.ground, modifier = modifier) {
        MonthPage(
            wrap = SpineFixtureWeek.monthWrap,
            lookups = SpineFixtureWeek.lookups,
            dayView = view,
            onDayView = { view = it },
            onWeek = {},
            onOpen = {},
            zone = SpineFixtureWeek.zone,
        )
    }
}

/** One fixture pebble on its own: no cache, no photo, the ratio primed. */
@Composable
internal fun FixturePebble(
    moment: PathMoment,
    modifier: Modifier = Modifier,
) {
    val requests = remember { StonePressRequests() }
    SpinePebble(
        moment = moment,
        palette = SpineFixtureWeek.palettes(moment),
        soulsById = SpineFixtureWeek.soulsById,
        personsOrder = SpineFixtureWeek.personsOrder,
        aspect = null,
        zone = SpineFixtureWeek.zone,
        pressRequests = requests,
        onOpen = { _, _ -> },
        onRequestDelete = {},
        modifier = modifier,
        ratioOverride = moment.firstSnapPath?.let { SpineFixtureWeek.ratios[it] },
        photoOverride = null to null,
    )
}

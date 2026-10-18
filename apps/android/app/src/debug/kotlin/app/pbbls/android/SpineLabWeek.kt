package app.pbbls.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.features.path.spine.SpinePebble
import app.pbbls.android.features.path.spine.SpineWeek
import app.pbbls.android.features.path.spine.StonePressRequests
import app.pbbls.android.features.path.spine.WeekNeighbour

/*
 * The fixture week on the Spine (#1122), for the Spine lab's Week tab and the
 * previews. No network: the ratios are primed and the photo URLs withheld, so the
 * cards show their fields. Both week links are live so the masthead and the mouth
 * render them; they go nowhere.
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
        onOpen = {},
        onRequestDelete = {},
        previousWeek = WeekNeighbour(SpineFixtureWeek.previousNumber) {},
        nextWeek = WeekNeighbour(SpineFixtureWeek.nextNumber) {},
        onCreatePebble = {},
        modifier = modifier,
        zone = SpineFixtureWeek.zone,
        ratioOverride = SpineFixtureWeek.ratios::get,
    )
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
        onOpen = {},
        onRequestDelete = {},
        modifier = modifier,
        ratioOverride = moment.firstSnapPath?.let { SpineFixtureWeek.ratios[it] },
        photoOverride = null to null,
    )
}

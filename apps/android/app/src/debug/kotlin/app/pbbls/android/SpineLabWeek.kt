package app.pbbls.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.features.path.spine.SpinePebble
import app.pbbls.android.features.path.spine.SpineWeek
import app.pbbls.android.features.path.spine.StonePressRequests
import app.pbbls.android.features.path.spine.WeekNeighbour
import app.pbbls.android.features.path.wrap.intro.IntroHapticTier
import app.pbbls.android.features.path.wrap.intro.IntroLedger
import app.pbbls.android.features.path.wrap.intro.rememberIntroVibrator
import app.pbbls.android.features.path.wrap.month.MonthIntro
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

/**
 * The fixture's October page (#1207): the lab's Month tab, the day view switchable but not saved. It is
 * dealt as the month's deck (#1242) with its own ledger: Replay deals it again from the cover, and a chip
 * forces a haptic tier (Auto is the phone's own) and deals it again in that tier.
 */
@Composable
internal fun SpineLabMonth(modifier: Modifier = Modifier) {
    var view by remember { mutableStateOf(DayView.CIRCLE) }
    val ledger = remember { IntroLedger() }
    var run by remember { mutableIntStateOf(0) }
    var forced by remember { mutableStateOf<IntroHapticTier?>(null) }
    val haptics = rememberIntroVibrator(forced = forced)
    val intro = remember(haptics) { MonthIntro(ledger, haptics) }
    val month = SpineFixtureWeek.monthWrap.month
    Column(modifier) {
        Row(
            Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(onClick = {
                ledger.reset(month)
                run += 1
            }) { Text("Replay") }
            listOf(null, IntroHapticTier.COMPOSITION, IntroHapticTier.WAVEFORM, IntroHapticTier.VIEW).forEach { tier ->
                FilterChip(
                    selected = forced == tier,
                    onClick = {
                        // One new clock for both changes: the tier's vibrator and the run.
                        forced = tier
                        ledger.reset(month)
                        run += 1
                    },
                    label = { Text(tier?.name?.take(1) ?: "Auto") },
                )
            }
        }
        Text("tier ${haptics.tier}", Modifier.padding(horizontal = 12.dp))
        Surface(color = PebblesTheme.spine.colors.ground, modifier = Modifier.weight(1f)) {
            // Each run is a new page: a new clock, and the deck dealt again from the cover.
            key(run) {
                MonthPage(
                    wrap = SpineFixtureWeek.monthWrap,
                    lookups = SpineFixtureWeek.lookups,
                    dayView = view,
                    onDayView = { view = it },
                    onWeek = {},
                    onOpen = {},
                    modifier = Modifier.fillMaxSize(),
                    intro = intro,
                    zone = SpineFixtureWeek.zone,
                )
            }
        }
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

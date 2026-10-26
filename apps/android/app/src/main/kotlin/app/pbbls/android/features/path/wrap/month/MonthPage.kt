package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.data.SnapAspectCache
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.intro.IntroClock
import app.pbbls.android.features.path.wrap.intro.IntroDrive
import app.pbbls.android.features.path.wrap.intro.IntroHapticPlaying
import app.pbbls.android.features.path.wrap.intro.IntroLedger
import app.pbbls.android.features.path.wrap.intro.rememberIntroAllowed
import java.time.LocalDate
import java.time.ZoneId

private val PAGE_BOTTOM = 96.dp

/** What a month page needs to play its intro (#1242 D20): the launch's ledger and the haptics. Null: no intro. */
@Immutable
class MonthIntro(
    val ledger: IntroLedger,
    val haptics: IntroHapticPlaying?,
)

/**
 * A month's page (#1207, D6). With an [intro] the policy allows, the month is a deck of cards
 * (`MonthDeckPager`, #1242 D21) whose intro plays while the page is [focused] and the activity resumed,
 * and a tap anywhere lands it. Without one, or under Reduce Motion, TalkBack or a large text size, it is
 * the plain page, settled: the cover, then the sections the server listed, in its order, the tone and
 * the hours under one *insights* head.
 */
@Composable
fun MonthPage(
    wrap: MonthWrap,
    lookups: WrapLookups,
    dayView: DayView,
    onDayView: (DayView) -> Unit,
    onWeek: (LocalDate) -> Unit,
    onOpen: (PathMoment) -> Unit,
    modifier: Modifier = Modifier,
    intro: MonthIntro? = null,
    focused: Boolean = true,
    zone: ZoneId = ZoneId.systemDefault(),
    aspect: SnapAspectCache? = null,
) {
    // The sheet's open tab, kept across a rotation; never in the ViewModel (spec §7).
    var sheet by rememberSaveable(wrap.month) { mutableStateOf<String?>(null) }
    val onMore: (MonthSection) -> Unit = { sheet = it.name }
    val allowed = rememberIntroAllowed()
    val clock =
        remember(wrap.month, intro, allowed) {
            if (intro != null && allowed) IntroClock(wrap.month, intro.ledger, intro.haptics) else null
        }

    if (clock != null) {
        // The active flag lives here, outside the deck, so nothing the deck redraws can drop it (D21's visibility trap).
        DriveWhileActive(clock, focused)
        MonthDeckPager(wrap, lookups, clock, dayView, zone, aspect, onWeek, onOpen, onMore, modifier)
    } else {
        LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = PAGE_BOTTOM)) {
            item(key = "cover") { MonthCover(wrap, lookups) }
            items(wrap.sections, key = { it.name }) { section ->
                MonthSectionContent(section, wrap, lookups, dayView, zone, aspect, onWeek, onOpen, onMore)
            }
        }
    }

    val open = sheet?.let { name -> MonthSection.entries.firstOrNull { it.name == name } }
    if (open != null) {
        MonthSheet(
            wrap = wrap,
            lookups = lookups,
            dayView = dayView,
            onDayView = onDayView,
            initial = open,
            onDismiss = { sheet = null },
            onPageChange = { sheet = it.name },
        )
    }
}

/**
 * Drives [clock], active while the page is [focused] and the activity resumed (D20). Its own scope, so a
 * pause or a resume recomposes only this.
 */
@Composable
private fun DriveWhileActive(
    clock: IntroClock,
    focused: Boolean,
) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    IntroDrive(clock, active = focused && lifecycle.isAtLeast(Lifecycle.State.RESUMED))
}

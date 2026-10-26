package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import app.pbbls.android.features.path.wrap.intro.IntroHost
import app.pbbls.android.features.path.wrap.intro.IntroSequence
import app.pbbls.android.features.path.wrap.intro.onAnyTap
import app.pbbls.android.features.path.wrap.week.rememberWrapCopy
import java.time.LocalDate
import java.time.ZoneId

private val PAGE_BOTTOM = 96.dp

/**
 * A month's page (#1207, D6): the cover, then the sections the server listed, in its order, the
 * tone and the hours under one *insights* head. With an [intro] clock the cover plays its intro
 * once the page is resumed, and a tap anywhere lands it; without one, the page is settled.
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
    intro: IntroClock? = null,
    zone: ZoneId = ZoneId.systemDefault(),
    aspect: SnapAspectCache? = null,
) {
    // The sheet's open tab, kept across a rotation; never in the ViewModel (spec §7).
    var sheet by rememberSaveable(wrap.month) { mutableStateOf<String?>(null) }
    val copy = rememberWrapCopy()
    val onMore: (MonthSection) -> Unit = { sheet = it.name }
    if (intro != null) {
        DriveWhileResumed(intro)
        // The cover is the page's only card for now: the deck's top card from the start.
        LaunchedEffect(intro) { intro.show(IntroSequence.Cover) }
    }

    LazyColumn(
        modifier = if (intro != null) modifier.onAnyTap(intro::skip) else modifier,
        contentPadding = PaddingValues(bottom = PAGE_BOTTOM),
    ) {
        item(key = "cover") {
            IntroHost(intro, IntroSequence.Cover, { coverBeats(wrap, lookups) }) { MonthCover(wrap, lookups) }
        }
        items(wrap.sections, key = { it.name }) { section ->
            when (section) {
                MonthSection.WEEKS -> WeeksSummary(wrap, lookups, onWeek)
                MonthSection.MILESTONES -> MilestonesSection(wrap, lookups, zone, onOpen, aspect)
                MonthSection.PERSONS -> PersonsSection(wrap, lookups, dayView, onMore)
                MonthSection.DOMAINS -> DomainsSection(wrap, lookups, onMore)
                MonthSection.GEM -> EmotionGem(wrap, lookups, onMore)
                MonthSection.TONE, MonthSection.HOURS -> {
                    // One head for both figures, over whichever comes first.
                    if (section == wrap.sections.firstOrNull { it == MonthSection.TONE || it == MonthSection.HOURS }) {
                        MonthSectionHead(copy.insights(), copy.insightsHint())
                    }
                    if (section == MonthSection.TONE) ToneFigure(wrap) else HoursFigure(wrap, lookups)
                }
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

/** Drives [intro] while the activity is resumed. Its own scope, so a pause or a resume recomposes only this. */
@Composable
private fun DriveWhileResumed(intro: IntroClock) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    IntroDrive(intro, active = lifecycle.isAtLeast(Lifecycle.State.RESUMED))
}

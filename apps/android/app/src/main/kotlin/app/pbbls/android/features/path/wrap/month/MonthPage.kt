package app.pbbls.android.features.path.wrap.month

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private val PAGE_BOTTOM = 96.dp

/**
 * What a month page needs to play its intro (#1242 D20): the launch's ledger and the haptics. Null: no intro.
 * Stable, not immutable: the ledger it holds records what plays.
 */
@Stable
class MonthIntro(
    val ledger: IntroLedger,
    val haptics: IntroHapticPlaying?,
)

/**
 * A month's page (#1207, D6). With an [intro] the policy allows, the month is a deck of cards
 * (`MonthDeckPager`, #1242 D21) whose intro plays while the page is [focused] and the activity resumed,
 * and a tap anywhere lands it. The end card's Replay (D14) forgets the month and deals the deck again from
 * the cover, on a fresh clock. Without one, or under Reduce Motion, TalkBack or a large text size, it is
 * the plain page, settled: the cover, then the sections the server listed, in its order, the tone and
 * the hours under one *insights* head. [focused] is required: a host that gave an [intro] and left the page's
 * focus to a default would play it off screen (the Path passes its settled page, the Lab `true`).
 */
@Composable
fun MonthPage(
    wrap: MonthWrap,
    lookups: WrapLookups,
    dayView: DayView,
    onDayView: (DayView) -> Unit,
    onWeek: (LocalDate) -> Unit,
    onOpen: (PathMoment) -> Unit,
    focused: Boolean,
    modifier: Modifier = Modifier,
    intro: MonthIntro? = null,
    zone: ZoneId = ZoneId.systemDefault(),
    aspect: SnapAspectCache? = null,
) {
    // The sheet's open tab, kept across a rotation; never in the ViewModel (spec §7).
    var sheet by rememberSaveable(wrap.month) { mutableStateOf<String?>(null) }
    val onMore: (MonthSection) -> Unit = { sheet = it.name }
    val allowed = rememberIntroAllowed()

    if (intro != null && allowed) {
        // The deck's place is saved within a launch (the Path's pager restores a page it scrolled past, a
        // rotation), never across one: after the process died the ledger is empty, and a deck reopened on its
        // last card would play that card and never the cover. A new launch deals it again from the cover, as iOS.
        key(intro.ledger.launch) {
            // Each Replay deals the deck again, from the cover, on a fresh clock (D14). Saved, like the sheet: the
            // run is in the deck's key, so a run back at 0 would lose the deck's place and reopen a replayed month
            // on its cover.
            var run by rememberSaveable(wrap.month) { mutableIntStateOf(0) }
            val clock = remember(wrap.month, intro, run) { IntroClock(wrap.month, intro.ledger, intro.haptics) }
            val scope = rememberCoroutineScope()

            // The active flag lives here, outside the replayed deck, so the new deck inherits it and nothing the deck
            // redraws can drop it (D21's visibility trap): a replayed deck plays its cover at once. It drives the
            // newest clock only; a deck fading out keeps its own, retired.
            DriveWhileActive(clock, focused)
            val deal = remember(clock) { MonthDeal(wrap.month, run, clock) }
            AnimatedContent(
                targetState = deal,
                modifier = modifier,
                transitionSpec = {
                    // A replay cross-fades the decks (iOS: 0.3 s ease-in-out); anything else swaps at once.
                    val replay = targetState.month == initialState.month && targetState.run > initialState.run
                    val transform =
                        if (replay) {
                            fadeIn(tween(REPLAY_FADE_MS, easing = EaseInOut)) togetherWith
                                fadeOut(tween(REPLAY_FADE_MS, easing = EaseInOut))
                        } else {
                            EnterTransition.None togetherWith ExitTransition.None
                        }
                    // The new deck lies over the one fading out.
                    transform.apply { targetContentZIndex = targetState.run.toFloat() } using null
                },
                // The month in the key, as in the pager's own keys (#1241's trap: decks sharing an identity sent a swipe
                // to August back to September), and the run, which deals a new deck, its pager on the cover. Never the
                // clock: the key is part of the deck's saved state, which a restored page must find again.
                contentKey = { it.month to it.run },
                label = "MonthDeckReplay",
            ) { dealt ->
                // A deck fading out says nothing: its Replay is gone as soon as it is tapped.
                val leaving = transition.targetState == EnterExitState.PostExit
                MonthDeckPager(
                    wrap,
                    lookups,
                    // Its own clock, never the page's: on the new one, a fading deck would register its cards and show its end.
                    dealt.clock,
                    dayView,
                    zone,
                    aspect,
                    onWeek,
                    onOpen,
                    onMore,
                    onReplay = {
                        scope.launch {
                            // The skip from this same tap lands first, on this deck's clock; replay on the next frame.
                            withFrameNanos { }
                            // Once per deck: a second tap before the new deck is dealt replays nothing more.
                            if (dealt.run != run) return@launch
                            // Stopped, its haptics with it, and its cards kept as they are while its deck fades out.
                            dealt.clock.retire()
                            intro.ledger.reset(dealt.month)
                            run += 1
                        }
                    },
                    modifier = Modifier.fillMaxSize().then(if (leaving) Modifier.clearAndSetSemantics {} else Modifier),
                )
            }
        }
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

/** One deal of a month's deck (D14): the run Replay counts, and the clock it plays on. */
@Stable
private class MonthDeal(
    val month: YearMonth,
    val run: Int,
    val clock: IntroClock,
)

/** The replayed deck's cross-fade (iOS `MonthPage.replay`'s `.easeInOut(duration: 0.3)`). */
private const val REPLAY_FADE_MS = 300

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

package app.pbbls.android.features.path.wrap.month

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.data.SnapAspectCache
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.copy.WrapCopy
import app.pbbls.android.features.path.wrap.intro.IntroBeat
import app.pbbls.android.features.path.wrap.intro.IntroClock
import app.pbbls.android.features.path.wrap.intro.IntroHost
import app.pbbls.android.features.path.wrap.intro.IntroScore
import app.pbbls.android.features.path.wrap.intro.IntroSequence
import app.pbbls.android.features.path.wrap.intro.head
import app.pbbls.android.features.path.wrap.intro.onAnyTap
import app.pbbls.android.features.path.wrap.week.rememberWrapCopy
import java.time.LocalDate
import java.time.ZoneId

/** The month's deck (#1242 D21; iOS `MonthDeck`'s statics): which cards it deals, and what each plays. */
internal object MonthDeck {
    /** The cover if shown, each section that shows in the server's order, then the end card; nothing for an empty month. */
    fun cards(
        wrap: MonthWrap,
        lookups: WrapLookups,
    ): List<IntroSequence> {
        val cover = if (wrap.cover != null) listOf(IntroSequence.Cover) else emptyList()
        val dealt = cover + wrap.sections.filter { MonthSections.isShown(it, wrap, lookups) }.map { IntroSequence.Section(it) }
        return if (dealt.isEmpty()) emptyList() else dealt + IntroSequence.End
    }

    /**
     * The first tone or hours card carries the insights head. Over the dealt [cards], so it agrees with
     * [MonthSections.leadsInsights], which reads the same condition off the wrap.
     */
    fun leadsInsights(
        card: IntroSequence,
        cards: List<IntroSequence>,
    ): Boolean =
        card ==
            cards.firstOrNull {
                it == IntroSequence.Section(MonthSection.TONE) || it == IntroSequence.Section(MonthSection.HOURS)
            }

    /**
     * A card's title (iOS `MonthDeck.head`): its section's head, as the page draws it; the insights head on the
     * card that leads them; none on the cover, whose title is the month, nor on the end card.
     */
    fun head(
        card: IntroSequence,
        wrap: MonthWrap,
        lookups: WrapLookups,
        copy: WrapCopy,
        dayView: DayView,
        leadsInsights: Boolean,
    ): MonthHead? =
        when (card) {
            IntroSequence.Cover, IntroSequence.End -> null
            is IntroSequence.Section ->
                if (leadsInsights) MonthSections.insightsHead(copy) else MonthSections.head(card.section, wrap, lookups, copy, dayView)
        }

    /**
     * What a card plays. The cover its garland; the weeks their columns, one at a time; the persons their rows
     * in turn; the gem its count, then its families; the tone its sweep and the hours their stones, under the
     * insights head when they lead it; another section, for now, its arrival alone (its entrance lands in the
     * parts after this one); the end card nothing yet.
     */
    fun beats(
        card: IntroSequence,
        wrap: MonthWrap,
        lookups: WrapLookups,
        leadsInsights: Boolean,
    ): List<IntroBeat> =
        when (card) {
            IntroSequence.Cover -> coverBeats(wrap, lookups)
            IntroSequence.Section(MonthSection.WEEKS) -> WeeksSummary.introBeats(wrap)
            IntroSequence.Section(MonthSection.PERSONS) -> PersonsSection.introBeats(wrap, lookups)
            IntroSequence.Section(MonthSection.GEM) -> EmotionGem.introBeats(wrap)
            IntroSequence.Section(MonthSection.TONE) -> ToneFigure.introBeats(wrap, leadsInsights)
            IntroSequence.Section(MonthSection.HOURS) -> HoursFigure.introBeats(wrap, lookups, leadsInsights)
            is IntroSequence.Section -> IntroScore.head(hasLead = false, insights = leadsInsights)
            IntroSequence.End -> emptyList()
        }
}

/**
 * The month as a deck of cards (#1242 D21; iOS `MonthDeck`): a vertical pager, one card a page, one per
 * fling (the pager's default snap). The top card lies over the next, which waits beneath it held in
 * place, a little smaller, its foot peeking; the top leaves upward under the finger, lifted and turned a
 * little; up to two static edges below say how many remain. A card arrives once a throw settles on it:
 * the clock shows it, and the throw ticks. Only vertical, so the Path's pager still takes horizontal
 * swipes. A tap anywhere skips, and still does its job.
 *
 * The top card is read off the pager's settled page, never driven into it (iOS: never a `scrollPosition`
 * binding). The end card has no content until the replay lands, so it is not dealt yet.
 */
@Composable
internal fun MonthDeckPager(
    wrap: MonthWrap,
    lookups: WrapLookups,
    clock: IntroClock,
    dayView: DayView,
    zone: ZoneId,
    aspect: SnapAspectCache?,
    onWeek: (LocalDate) -> Unit,
    onOpen: (PathMoment) -> Unit,
    onMore: (MonthSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = rememberWrapCopy()
    val cards = remember(wrap, lookups) { MonthDeck.cards(wrap, lookups).filter { it != IntroSequence.End } }
    if (cards.isEmpty()) return
    val pager = rememberPagerState { cards.size }
    val month = remember(wrap.month, copy) { "${copy.monthName(wrap.month)} ${wrap.month.year}" }

    // Arrival: the card a throw settles on plays; the one it covered, or uncovered, lands. A throw lands on a
    // tick: `landed` after `show`, which is what starts a card that ticks its own. Keyed on the cards too, so a
    // wrap that deals other cards shows the top one again (a host that left took the top card with it).
    LaunchedEffect(pager, clock, cards) {
        var previous: IntroSequence? = null
        snapshotFlow { pager.settledPage }.collect { page ->
            val card = cards.getOrElse(page) { cards.last() }
            clock.show(card)
            if (previous != null && previous != card) clock.landed(card)
            previous = card
        }
    }

    Box(modifier.onAnyTap(clock::skip)) {
        MonthDeckEdges(MonthDeckMotion.edges(below = cards.size - 1 - pager.settledPage), Modifier.fillMaxSize())
        VerticalPager(
            state = pager,
            // The month keeps the keys unique per month page (#1241's trap: a swipe to August sent back to September).
            key = { "${wrap.month}-${cards[it]}" },
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            val card = cards[index]
            val leads = MonthDeck.leadsInsights(card, cards)
            MonthDeckCard(
                month = month,
                step = MonthDeckStep.of(card, cards),
                head = MonthDeck.head(card, wrap, lookups, copy, dayView, leads),
                modifier =
                    Modifier
                        // An earlier card lies over a later one.
                        .zIndex((cards.size - index).toFloat())
                        // Only the top card speaks to Switch and Voice Access: the one beneath waits under it.
                        // (TalkBack never sees the deck: touch exploration draws the plain page.)
                        .then(if (index == pager.settledPage) Modifier else Modifier.clearAndSetSemantics {})
                        .graphicsLayer {
                            // The layer is the page: its height is the page's, its bottom the page's bottom.
                            val d = pager.getOffsetDistanceInPages(index)
                            val s = MonthDeckMotion.scale(d)
                            scaleX = s
                            scaleY = s
                            rotationZ = MonthDeckMotion.turn(d)
                            translationY = MonthDeckMotion.offset(d, size.height, MonthDeckMotion.PEEK.dp.toPx())
                            transformOrigin = if (d > 0) TransformOrigin(0.5f, 1f) else TransformOrigin.Center
                        }
                        // The page takes the touch, as iOS's paper does: a tap anywhere on the top card's page (the
                        // next card's peeking foot included) stops there, and never reaches the card held beneath it
                        // (a week, a milestone). Inside the layer, so it follows the card as it moves; it consumes
                        // nothing, so the deck's skip and the pager's throws still see every touch. Never a card
                        // already thrown: still composed beyond the viewport, lifted and turned above the top card,
                        // its page dips into the top of the deck, deepest at its left, and would take the touch there
                        // unseen.
                        .then(if (index >= pager.settledPage) Modifier.takesTouch() else Modifier),
            ) { room ->
                IntroHost(clock, card, { MonthDeck.beats(card, wrap, lookups, leads) }) {
                    when (card) {
                        IntroSequence.Cover -> MonthCover(wrap, lookups)
                        is IntroSequence.Section ->
                            MonthSectionContent(
                                card.section,
                                wrap,
                                lookups,
                                dayView,
                                zone,
                                aspect,
                                onWeek,
                                onOpen,
                                onMore,
                                leadsInsights = leads,
                                onCard = true,
                                room = room,
                            )
                        IntroSequence.End -> Unit
                    }
                }
            }
        }
    }
}

/**
 * Makes this a touch target that does nothing with what it gets: Compose hit-tests the siblings under a pointer
 * from the top until one has a pointer input there, and a card's page has none of its own.
 */
private fun Modifier.takesTouch(): Modifier =
    pointerInput(Unit) {
        awaitEachGesture { awaitFirstDown(requireUnconsumed = false) }
    }

/**
 * The cards past the next, as static edges below its foot (iOS `MonthDeckEdges`), drawn behind the deck,
 * the deepest first so each lies under the one above it. The count changes once a throw lands: an edge
 * fades rather than blinks. Decorative.
 */
@Composable
private fun MonthDeckEdges(
    count: Int,
    modifier: Modifier,
) {
    Box(modifier.clearAndSetSemantics {}) {
        (MonthDeckMotion.MAX_EDGES downTo 1).forEach { depth ->
            AnimatedVisibility(
                visible = depth <= count,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(EDGE_FADE_MS, easing = EaseOut)),
                exit = fadeOut(tween(EDGE_FADE_MS, easing = EaseOut)),
            ) {
                // The paper carries its inset, so this transform is the whole page's, scaled about its bottom.
                MonthDeckPaper(
                    Modifier.graphicsLayer {
                        val s = 1 - MonthDeckMotion.SHRINK * depth
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        translationY = (MonthDeckMotion.PEEK * depth).dp.toPx()
                    },
                )
            }
        }
    }
}

/** An edge's fade as the count changes (iOS `.easeOut(duration: 0.2)`). */
private const val EDGE_FADE_MS = 200

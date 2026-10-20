package app.pbbls.android.features.path.card

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.ui.render.stone.Frames
import app.pbbls.android.core.ui.render.stone.StoneGeometry
import app.pbbls.android.core.ui.render.stone.StoneMotion
import app.pbbls.android.core.ui.render.stone.rememberStoneOutline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first

/**
 * Where a print stands (pebble card spec §Completion), each phase 0 to 1: [print] the card
 * appearing in the place, [stone] the dashed stone's flight to the clearing and the lit
 * stone's press, [rise] the card going home while the page steps aside.
 */
@Immutable
internal data class CompletionProgress(
    val print: Float,
    val stone: Float,
    val rise: Float,
) {
    companion object {
        /** Printed and home: what a card that is not printing reads. */
        val REST = CompletionProgress(1f, 1f, 1f)
    }
}

/**
 * The print's one timeline (pebble card spec §Completion): a linear 0 to 1 over
 * [PRINT_MS] mapped through [StoneMotion.piecewise] tables, as the Spine's press is, so
 * every part reads the same clock and nothing drifts. [print] runs over 0–0.35, [stone]
 * over 0.25–0.6 with the press fired at [PRESS_AT], [rise] over 0.6–1.
 */
internal object CompletionMotion {
    const val PRINT_MS = 1600

    /** Reduced motion, or a place that cannot be shown whole: the page crossfades to the card. */
    const val CROSSFADE_MS = 250

    private const val PRINT_END = .35f
    private const val STONE_START = .25f
    private const val STONE_END = .6f

    /** The verso stone presses in here, on the timeline. */
    const val PRESS_AT = .45f

    val PRINT_FRAMES: Frames = listOf(0f to 0f, PRINT_END to 1f)
    val STONE_FRAMES: Frames = listOf(STONE_START to 0f, STONE_END to 1f)
    val RISE_FRAMES: Frames = listOf(STONE_END to 0f, 1f to 1f)

    /** [PRESS_AT] in the stone phase: the dashed stone lands, and the lit stone presses in. */
    const val LANDING = (PRESS_AT - STONE_START) / (STONE_END - STONE_START)

    private const val LIT_LEAD = .1f
    private const val GHOST_TAIL = .2f

    /** Within [CompletionProgress.stone]: the flight, the flying outline's alpha, the lit stone's. */
    val FLIGHT_FRAMES: Frames = listOf(0f to 0f, LANDING to 1f)
    val GHOST_ALPHA_FRAMES: Frames = listOf(0f to 1f, LANDING to 1f, LANDING + GHOST_TAIL to 0f)
    val LIT_STONE_FRAMES: Frames = listOf(LANDING - LIT_LEAD to 0f, LANDING to 1f)

    /** Within [CompletionProgress.rise]: the page fades as the card climbs, and steps back. */
    val PAGE_ALPHA_FRAMES: Frames = listOf(0f to 1f, .6f to 0f)
    const val PAGE_SCALE_END = .97f

    fun at(timeline: Float): CompletionProgress = CompletionProgress(print(timeline), stone(timeline), rise(timeline))

    /** The phases one at a time, for draw blocks: no [CompletionProgress] allocated per frame. */
    fun print(timeline: Float): Float = StoneMotion.piecewise(timeline, PRINT_FRAMES)

    fun stone(timeline: Float): Float = StoneMotion.piecewise(timeline, STONE_FRAMES)

    fun rise(timeline: Float): Float = StoneMotion.piecewise(timeline, RISE_FRAMES)

    fun litStoneAlpha(stone: Float): Float = StoneMotion.piecewise(stone, LIT_STONE_FRAMES)
}

/**
 * The parts that print in turn across [CompletionProgress.print] (spec §Completion 3): the
 * card itself first, then the lockup, the postmarks, the band and the footer.
 */
internal enum class PrintPart(
    private val frames: Frames,
) {
    CARD(listOf(0f to 0f, .4f to 1f)),
    LOCKUP(listOf(.25f to 0f, .55f to 1f)),
    POSTMARKS(listOf(.45f to 0f, .75f to 1f)),
    BAND(listOf(.6f to 0f, .9f to 1f)),
    FOOTER(listOf(.7f to 0f, 1f to 1f)),
    ;

    fun alpha(print: Float): Float = StoneMotion.piecewise(print, frames)
}

/**
 * One print of the card into a completed moment's place: the timeline, whether it
 * crossfades instead, and the live coordinates of what it moves between. The coordinates
 * are the nodes' own, read at draw time, so a scroll or a relayout during the print can
 * never leave a stale rectangle, and the page's and the card's spaces meet in one box
 * ([CardCompletion]'s) without the caller converting anything.
 *
 * @param frozenAt a print frozen part way, for previews: the full choreography, never played.
 */
@Stable
internal class CardCompletionState(
    frozenAt: Float? = null,
) {
    private val frozen = frozenAt != null
    private val timeline = Animatable(frozenAt ?: 0f)

    /** A crossfade instead of the print: reduced motion, or the place could not be shown whole. */
    var crossfade by mutableStateOf(false)
        private set

    /**
     * Where the print's timeline stands, 0 to 1; 1 (printed and home) while it crossfades. A
     * state read: read it once per draw or layer block and map it through [CompletionMotion].
     */
    val t: Float
        get() = if (crossfade) 1f else timeline.value

    /** The print's phases, for tests and previews; draw blocks read [t]. */
    val progress: CompletionProgress
        get() = CompletionMotion.at(t)

    /** The crossfade's 0 to 1; 1 while it prints. */
    val fade: Float
        get() = if (crossfade) timeline.value else 1f

    internal var box: LayoutCoordinates? = null

    /** The part of the box the reader sees: the box less what the system bars still cover at its foot. */
    internal var view: LayoutCoordinates? = null
    internal var place: LayoutCoordinates? = null

    /** Scrolls the page until the place shows; the place's own node, as the page is not the print's to scroll. */
    internal val placeRequester = BringIntoViewRequester()
    internal var ghost: LayoutCoordinates? = null
    internal var card: LayoutCoordinates? = null
    internal var stone: LayoutCoordinates? = null

    /**
     * Plays the print once, from the first frame laid out, so the place has bounds: the
     * whole choreography, or under [reduceMotion] a crossfade (spec §Completion, last line).
     * Returns when the card is home.
     *
     * The print happens in the place, so it plays only with the whole place in [view]: the
     * dashed stone's flight starts in it and the card feeds out of it, and a place cut by the
     * screen's foot hid both behind the navigation bar. A place not whole on screen is
     * brought into view first, the page scrolling while the card is still unprinted (nothing
     * of it drawn, and the page under it held); one taller than the view, or one the page
     * cannot scroll to, crossfades instead.
     */
    suspend fun play(reduceMotion: Boolean) {
        if (frozen) return
        withFrameNanos { }
        if (!reduceMotion && !placeShown() && placeFits()) {
            bringPlaceIntoView()
            withFrameNanos { }
        }
        crossfade = reduceMotion || !placeShown()
        val ms = if (crossfade) CompletionMotion.CROSSFADE_MS else CompletionMotion.PRINT_MS
        timeline.animateTo(1f, tween(ms, easing = LinearEasing))
    }

    /** Returns when the verso stone should press in; never in a frozen print or a crossfade. */
    suspend fun awaitPress() {
        if (frozen) awaitCancellation()
        snapshotFlow { !crossfade && timeline.value >= CompletionMotion.PRESS_AT }.first { it }
    }

    private fun inBox(node: LayoutCoordinates?): Rect? {
        val b = box?.takeIf { it.isAttached } ?: return null
        val n = node?.takeIf { it.isAttached } ?: return null
        return b.localBoundingBoxOf(n, clipBounds = false)
    }

    /** The whole place shows, within a pixel: a print into a place cut by the view's edge happens partly off screen. */
    internal fun placeShown(): Boolean {
        val place = inBox(place) ?: return false
        val view = inBox(view) ?: return false
        return inBox(card) != null && place.top >= view.top - EDGE_SLOP && place.bottom <= view.bottom + EDGE_SLOP
    }

    /** The place is no taller than the view, so scrolling can show it whole. */
    private fun placeFits(): Boolean {
        val place = inBox(place) ?: return false
        val view = inBox(view) ?: return false
        return place.height <= view.height
    }

    /**
     * Scrolls the page until the place shows whole, its foot clear of what [view] leaves
     * out at the box's foot (the bar's inset where the page's own padding stands under it):
     * the page's scroll knows only its own viewport, the box.
     */
    private suspend fun bringPlaceIntoView() {
        val place = place?.takeIf { it.isAttached } ?: return
        val box = inBox(box) ?: return
        val view = inBox(view) ?: return
        val covered = (box.bottom - view.bottom).coerceAtLeast(0f)
        try {
            placeRequester.bringIntoView(Rect(0f, 0f, place.size.width.toFloat(), place.size.height + covered))
        } catch (e: CancellationException) {
            // Only our own cancellation may end the print here. A bring-into-view interrupted
            // by another scroll is swallowed by Compose today; were a later version to let it
            // escape, the print's effect would end with the card unprinted and the overlay
            // still eating input. The check after it decides, print or crossfade.
            currentCoroutineContext().ensureActive()
        }
    }

    /** The card's offset from its resting bounds at [rise]: at the place through the print, home over the rise. */
    fun cardOffset(rise: Float): Offset {
        val place = inBox(place) ?: return Offset.Zero
        val rest = inBox(card) ?: return Offset.Zero
        val away = Offset(place.center.x - rest.center.x, place.top - rest.top)
        return away * (1f - FastOutSlowInEasing.transform(rise))
    }

    /** How much of the card has fed out of the place at [print], from its top, in its own pixels. */
    fun revealed(
        print: Float,
        cardHeight: Float,
    ): Float {
        val place = inBox(place) ?: return cardHeight
        return lerp(place.height, cardHeight, FastOutSlowInEasing.transform(print)).coerceAtLeast(place.height)
    }

    /** The dashed stone's flight in the box at [t]: from the page's ghost to the verso's clearing, centre and width. */
    fun flight(t: Float): Pair<Offset, Float>? {
        val from = inBox(ghost) ?: return null
        val card = card?.takeIf { it.isAttached } ?: return null
        val stoneInCard = stone?.takeIf { it.isAttached }?.let { card.localBoundingBoxOf(it, clipBounds = false) } ?: return null
        val rest = inBox(this.card) ?: return null
        val to = stoneInCard.translate(rest.topLeft + cardOffset(CompletionMotion.rise(t)))
        val f = FastOutSlowInEasing.transform(StoneMotion.piecewise(CompletionMotion.stone(t), CompletionMotion.FLIGHT_FRAMES))
        return lerp(from.center, to.center, f) to lerp(from.width, to.width, f)
    }
}

/**
 * The completion (pebble card spec §Completion): over the bare page, the card appears
 * verso up at the place's bounds, the dashed stone flies to the clearing and the lit
 * stone presses in, the lockup, postmarks, band and footer print in turn, the page
 * steps aside and the card rises to its rest. Reduced motion: a crossfade.
 *
 * It hosts the moment's two layers in one box whether or not a print runs, each at one call
 * site, so neither is remounted when a print starts or ends (the page keeps its scroll, its
 * photo, its measured place): the bare [page] (its place and dashed stone found through the
 * two modifiers it is handed) and the [card] (handed the modifier that moves it). A print is
 * on while [completion] is set and both layers are; the caller keeps the page up for
 * exactly that long, and ignores its call to action meanwhile. Mid-print the page says
 * nothing to TalkBack and takes no focus, so nothing on it can be pressed under the card. The card lays out at rest the whole time and is drawn
 * away from it, so its resting bounds are always known and the page's place is read
 * in the same box: no frame shows the card at rest before the print, and the place's
 * bounds live as long as the page does. Input is held for the print's length, and for the
 * scroll that first brings the place into view.
 *
 * Shaped as a host over a state object rather than an overlay taking the place's bounds:
 * the bounds are the place's live coordinates, the timeline is the caller's to play (and
 * to cancel with the screen), and the card's call site never moves.
 */
@Composable
internal fun CardCompletion(
    completion: CardCompletionState?,
    page: (@Composable (placeModifier: Modifier, ghostModifier: Modifier) -> Unit)?,
    card: (@Composable (cardModifier: Modifier) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val printing = completion != null && page != null && card != null
    Box(modifier.then(if (completion != null && printing) Modifier.onPlaced { completion.box = it } else Modifier)) {
        if (page != null) {
            Box(Modifier.fillMaxSize().testTag(COMPLETION_PAGE_TAG)) {
                Box(Modifier.fillMaxSize().then(if (completion != null && printing) Modifier.pageLayer(completion) else Modifier)) {
                    page(
                        if (completion != null && printing) {
                            Modifier.bringIntoViewRequester(completion.placeRequester).onPlaced { completion.place = it }
                        } else {
                            Modifier
                        },
                        if (completion != null && printing) Modifier.onPlaced { completion.ghost = it } else Modifier,
                    )
                }
            }
        }
        card?.invoke(if (completion != null && printing) Modifier.cardLayer(completion) else Modifier)
        if (completion != null && printing) {
            PrintOverlay(completion, Modifier.fillMaxSize())
            // The view: the box less the inset left to the page's own padding at its foot (none above a bar).
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .onPlaced { completion.view = it },
            )
        }
    }
}

/**
 * The page under the print: whole until the rise, then fading and stepping back (or
 * crossfading out). Silent and unfocusable meanwhile: the card is coming over it.
 */
private fun Modifier.pageLayer(completion: CardCompletionState): Modifier =
    clearAndSetSemantics {}
        .focusProperties { onEnter = { cancelFocusChange() } }
        .focusGroup()
        .graphicsLayer {
            if (completion.crossfade) {
                alpha = 1f - completion.fade
            } else {
                val rise = CompletionMotion.rise(completion.t)
                alpha = StoneMotion.piecewise(rise, CompletionMotion.PAGE_ALPHA_FRAMES)
                val scale = lerp(1f, CompletionMotion.PAGE_SCALE_END, FastOutSlowInEasing.transform(rise))
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
        }

/** How far, in pixels, a place may run past the view's edge and still count as shown: layout rounding. */
private const val EDGE_SLOP = 1f

/** The bare page's layer while a print may run over it, for tests: its own contents say nothing mid-print. */
internal const val COMPLETION_PAGE_TAG = "completion_page"

/**
 * How far the shell's painting reaches past the card's bounds: its blurred shadow (the
 * Spine's `--spine-card-shadow`, 18 dp down with a 30 dp blur less 22 dp of spread) and its
 * painted edge lip below. The print's clip keeps clear of it.
 */
private val SHELL_OVERHANG = 32.dp

/**
 * The card under the print, laid out at rest and drawn at [CardCompletionState.cardOffset]:
 * moved in the draw, not the layer. It fades in ([PrintPart.CARD]) with the alpha applied
 * to each draw rather than through an offscreen buffer, which would clip the shell's
 * shadow and edge to the card's bounds, and feeds out of the place top first: the clip
 * cuts only the foot, clear of the shadow on the other three sides, grows past the foot by
 * the shadow's reach as the print ends, and is gone once the card is printed.
 */
private fun Modifier.cardLayer(completion: CardCompletionState): Modifier =
    onPlaced { completion.card = it }
        .drawWithContent {
            val offset = completion.cardOffset(CompletionMotion.rise(completion.t))
            translate(offset.x, offset.y) { this@drawWithContent.drawContent() }
        }.graphicsLayer {
            compositingStrategy = CompositingStrategy.ModulateAlpha
            alpha = if (completion.crossfade) completion.fade else PrintPart.CARD.alpha(CompletionMotion.print(completion.t))
        }.drawWithContent {
            val print = CompletionMotion.print(completion.t)
            if (print >= 1f) {
                drawContent()
            } else {
                val overhang = SHELL_OVERHANG.toPx()
                val bottom = completion.revealed(print, size.height) + overhang * FastOutSlowInEasing.transform(print)
                clipRect(left = -overhang, top = -overhang, right = size.width + overhang, bottom = bottom) {
                    this@drawWithContent.drawContent()
                }
            }
        }

/** The dashed stone in flight over the card, and a hand over the page so nothing is pressed mid-print. */
@Composable
private fun PrintOverlay(
    completion: CardCompletionState,
    modifier: Modifier,
) {
    val ink = PebblesTheme.spine.colors.ink3
    val outline = rememberStoneOutline(GHOST_SIZE, GHOST_POLARITY)
    val box = StoneGeometry.outlineBox(GHOST_SIZE)
    val density = LocalDensity.current
    val dashes = remember(density) { ghostDashes(density) }
    Canvas(
        modifier.pointerInput(completion) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        },
    ) {
        val t = completion.t
        val stone = CompletionMotion.stone(t)
        if (completion.crossfade || stone <= 0f || stone >= 1f) return@Canvas
        val alpha = StoneMotion.piecewise(stone, CompletionMotion.GHOST_ALPHA_FRAMES)
        if (alpha <= 0f) return@Canvas
        val (centre, width) = completion.flight(t) ?: return@Canvas
        val height = width * box.height / box.width
        translate(centre.x - width / 2f, centre.y - height / 2f) { drawGhostStone(outline.path, width, ink, dashes, alpha) }
    }
}

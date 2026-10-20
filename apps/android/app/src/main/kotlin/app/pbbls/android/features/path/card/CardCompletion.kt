package app.pbbls.android.features.path.card

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.util.lerp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.ui.render.stone.Frames
import app.pbbls.android.core.ui.render.stone.StoneGeometry
import app.pbbls.android.core.ui.render.stone.StoneMotion
import app.pbbls.android.core.ui.render.stone.rememberStoneOutline
import kotlinx.coroutines.awaitCancellation
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

    /** Reduced motion, or no place on screen: the page crossfades to the card. */
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

    fun at(timeline: Float): CompletionProgress =
        CompletionProgress(
            print = StoneMotion.piecewise(timeline, PRINT_FRAMES),
            stone = StoneMotion.piecewise(timeline, STONE_FRAMES),
            rise = StoneMotion.piecewise(timeline, RISE_FRAMES),
        )

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

    /** A crossfade instead of the print: reduced motion, or the place was not on screen. */
    var crossfade by mutableStateOf(false)
        private set

    /** The print's phases; [CompletionProgress.REST] while it crossfades. A state read: use it in draw and layer blocks. */
    val progress: CompletionProgress
        get() = if (crossfade) CompletionProgress.REST else CompletionMotion.at(timeline.value)

    /** The crossfade's 0 to 1; 1 while it prints. */
    val fade: Float
        get() = if (crossfade) timeline.value else 1f

    internal var box: LayoutCoordinates? = null
    internal var place: LayoutCoordinates? = null
    internal var ghost: LayoutCoordinates? = null
    internal var card: LayoutCoordinates? = null
    internal var stone: LayoutCoordinates? = null

    /**
     * Plays the print once, from the first frame laid out, so the place has bounds: the
     * whole choreography, or under [reduceMotion] or with the place off screen a
     * crossfade (spec §Completion, last line). Returns when the card is home.
     */
    suspend fun play(reduceMotion: Boolean) {
        if (frozen) return
        withFrameNanos { }
        crossfade = reduceMotion || !placeInView()
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

    /** At least part of the place shows: printing into a place scrolled away would happen off screen. */
    private fun placeInView(): Boolean {
        val place = inBox(place) ?: return false
        val height = box?.size?.height ?: return false
        return inBox(card) != null && place.bottom > 0f && place.top < height
    }

    /** The card's offset from its resting bounds: at the place through the print, home over the rise. */
    fun cardOffset(progress: CompletionProgress): Offset {
        val place = inBox(place) ?: return Offset.Zero
        val rest = inBox(card) ?: return Offset.Zero
        val away = Offset(place.center.x - rest.center.x, place.top - rest.top)
        return away * (1f - FastOutSlowInEasing.transform(progress.rise))
    }

    /** How much of the card has fed out of the place, from its top, in its own pixels. */
    fun revealed(
        progress: CompletionProgress,
        cardHeight: Float,
    ): Float {
        val place = inBox(place) ?: return cardHeight
        return lerp(place.height, cardHeight, FastOutSlowInEasing.transform(progress.print)).coerceAtLeast(place.height)
    }

    /** The dashed stone's flight in the box: from the page's ghost to the verso's clearing, centre and width. */
    fun flight(progress: CompletionProgress): Pair<Offset, Float>? {
        val from = inBox(ghost) ?: return null
        val card = card?.takeIf { it.isAttached } ?: return null
        val stoneInCard = stone?.takeIf { it.isAttached }?.let { card.localBoundingBoxOf(it, clipBounds = false) } ?: return null
        val rest = inBox(this.card) ?: return null
        val to = stoneInCard.translate(rest.topLeft + cardOffset(progress))
        val f = FastOutSlowInEasing.transform(StoneMotion.piecewise(progress.stone, CompletionMotion.FLIGHT_FRAMES))
        return lerp(from.center, to.center, f) to lerp(from.width, to.width, f)
    }
}

/**
 * The completion (pebble card spec §Completion): over the bare page, the card appears
 * verso up at the place's bounds, the dashed stone flies to the clearing and the lit
 * stone presses in, the lockup, postmarks, band and footer print in turn, the page
 * steps aside and the card rises to its rest. Reduced motion: a crossfade.
 *
 * It hosts the moment's two layers in one box whether or not a print runs, so neither is
 * remounted when one starts or ends: the bare [page] (its place and dashed stone found
 * through the two modifiers it is handed) and the [card] (handed the modifier that moves
 * it). A print is on while [completion] is set and both layers are; the caller keeps the
 * page up for exactly that long. The card lays out at rest the whole time and is drawn
 * away from it, so its resting bounds are always known and the page's place is read
 * in the same box: no frame shows the card at rest before the print, and the place's
 * bounds live as long as the page does. Input is held for the print's length.
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
            Box(Modifier.fillMaxSize().then(if (completion != null && printing) Modifier.pageLayer(completion) else Modifier)) {
                if (completion != null && printing) {
                    page(Modifier.onPlaced { completion.place = it }, Modifier.onPlaced { completion.ghost = it })
                } else {
                    page(Modifier, Modifier)
                }
            }
        }
        card?.invoke(if (completion != null && printing) Modifier.cardLayer(completion) else Modifier)
        if (completion != null && printing) PrintOverlay(completion, Modifier.fillMaxSize())
    }
}

/** The page under the print: whole until the rise, then fading and stepping back (or crossfading out). */
private fun Modifier.pageLayer(completion: CardCompletionState): Modifier =
    graphicsLayer {
        val p = completion.progress
        if (completion.crossfade) {
            alpha = 1f - completion.fade
        } else {
            alpha = StoneMotion.piecewise(p.rise, CompletionMotion.PAGE_ALPHA_FRAMES)
            val scale = lerp(1f, CompletionMotion.PAGE_SCALE_END, FastOutSlowInEasing.transform(p.rise))
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0.5f, 0f)
        }
    }

/**
 * The card under the print, laid out at rest and drawn at [CardCompletionState.cardOffset]:
 * moved in the draw, not the layer, so the layer's alpha never clips what is moved past
 * its bounds. It fades in ([PrintPart.CARD]) and feeds out of the place top first.
 */
private fun Modifier.cardLayer(completion: CardCompletionState): Modifier =
    onPlaced { completion.card = it }
        .drawWithContent {
            val offset = completion.cardOffset(completion.progress)
            translate(offset.x, offset.y) { this@drawWithContent.drawContent() }
        }.graphicsLayer {
            alpha = if (completion.crossfade) completion.fade else PrintPart.CARD.alpha(completion.progress.print)
        }.drawWithContent {
            val bottom = completion.revealed(completion.progress, size.height)
            clipRect(bottom = bottom) { this@drawWithContent.drawContent() }
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
    Canvas(
        modifier.pointerInput(completion) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        },
    ) {
        val p = completion.progress
        if (completion.crossfade || p.stone <= 0f || p.stone >= 1f) return@Canvas
        val alpha = StoneMotion.piecewise(p.stone, CompletionMotion.GHOST_ALPHA_FRAMES)
        if (alpha <= 0f) return@Canvas
        val (centre, width) = completion.flight(p) ?: return@Canvas
        val height = width * box.height / box.width
        translate(centre.x - width / 2f, centre.y - height / 2f) { drawGhostStone(outline.path, width, ink, alpha) }
    }
}

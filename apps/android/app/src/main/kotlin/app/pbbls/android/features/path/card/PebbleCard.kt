package app.pbbls.android.features.path.card

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.rememberReduceMotion
import app.pbbls.android.core.model.CardFace
import app.pbbls.android.core.model.Domain
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.MomentDetail
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.ui.render.stone.BLANK_STONE_PALETTE
import app.pbbls.android.core.ui.render.stone.StoneTones
import app.pbbls.android.core.ui.render.stone.toStonePalette
import app.pbbls.android.features.path.card.logic.TurnSettle
import app.pbbls.android.features.path.spine.SpineCardShell
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.sin

/** The band's paper: the spot into the stock, 19 % on paper and 24 % at Night (back-token3 `--band`). */
private const val BAND_PAPER = 0.19f
private const val BAND_PAPER_NIGHT = 0.24f

/** The turn's camera, in Skia's units per dp of density (pebble card spec §Behaviour). */
private const val TURN_CAMERA_DISTANCE = 12f

/** Pixels in one of Skia's camera units, the inch at 72 dpi (`SkCamera3D`), which HWUI's `cameraDistance` is in. */
private const val SKIA_CAMERA_UNIT = 72f

private const val HALF_TURN_DEG = 180f

private enum class FaceSlot { RECTO, VERSO, VERSO_PROBE, BAND }

/**
 * The held card (pebble card spec): a still shell, a face that shows the recto or the
 * verso, the collection band wrapped across the face's foot on the verso, and the still
 * footer.
 *
 * Both faces are composed at the card's width and the face area takes the taller, so
 * turning never changes the card's size; the verso fills that height, so its stone
 * centres. Only the face in front is placed, and only it speaks to TalkBack.
 *
 * The turn (pebble card spec §Behaviour, [CardTurnState]): the face area alone rotates on
 * Y inside the still shell, by the footer's button, a horizontal drag toward the start
 * edge (a flick carries it, a release short of the half reverts), or the card's TalkBack
 * action; the face in front swaps at 90°, edge-on. Under reduced motion the faces
 * crossfade instead. While it turns, the face shrinks by exactly what perspective adds to
 * its near edge, so the near corners stay inside the shell's clip rather than being cut by
 * it, and the far edge recedes as on a real card.
 *
 * The footer names the face that is not in front: it switches at the half, with the
 * picture, so a drag held past the half offers the turn back.
 *
 * @param detail a moment with a pebble: only a pebble has a card.
 * @param ratio the photo's width / height; null until probed.
 * @param photoUrl the signed original; null until signed.
 * @param thumbUrl the signed thumb, the photo's placeholder.
 * @param soulsById the souls cache, for the recto's marks.
 * @param domains the reference domains, for the postmarks' glyphs.
 * @param requestedFace the face showing first, and the face the card turns to when it
 *   changes after: beside Path the detail entry stays while its key's face changes (a
 *   stone pressed after its card). Only a change counts, so pressing the face already
 *   asked for again does nothing, even on a card turned by hand since.
 * @param zone where the recto's day, time and dateline fall, as `SpineWeek` takes its own.
 * @param turnProgressOverride a turn frozen part way, for previews; it shows the rotation
 *   even where previews report reduced motion.
 */
@Composable
internal fun PebbleCard(
    detail: MomentDetail,
    palette: EmotionPalette?,
    ratio: Float?,
    photoUrl: String?,
    thumbUrl: String?,
    soulsById: Map<String, SoulWithGlyph>,
    domains: List<Domain>,
    modifier: Modifier = Modifier,
    requestedFace: CardFace = CardFace.RECTO,
    zone: ZoneId = ZoneId.systemDefault(),
    turnProgressOverride: Float? = null,
) {
    val pebble = requireNotNull(detail.pebble) { "only a pebble has a card" }
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion() && turnProgressOverride == null
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val turn =
        rememberSaveable(saver = CardTurnState.saver(scope)) {
            CardTurnState(requestedFace, scope, initialProgress = turnProgressOverride ?: 0f)
        }
    // Saved with the face, so a restored card turned by hand is not turned back to the request.
    var answered by rememberSaveable { mutableStateOf(requestedFace) }
    LaunchedEffect(requestedFace) {
        if (requestedFace != answered) {
            answered = requestedFace
            turn.turnTo(requestedFace, reduceMotion)
        }
    }
    val colors = PebblesTheme.spine.colors
    val stone = remember(palette) { palette?.toStonePalette() ?: BLANK_STONE_PALETTE }
    val spot = StoneTones.spot(stone, colors.isNight)
    val turnAction =
        stringResource(
            if (turn.front == CardFace.RECTO) R.string.card_turn_action_to_pebble else R.string.card_turn_action_to_moment,
        )

    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val width = min(maxWidth - CardLayout.PAGE_MARGIN * 2, CardLayout.MAX_WIDTH).coerceAtLeast(0.dp)
        val u = width / CardLayout.U_DIVISOR
        val area: @Composable (shown: () -> CardFace, areaModifier: Modifier) -> Unit = { shown, areaModifier ->
            FaceArea(
                shown = shown,
                u = u,
                recto = {
                    FaceContent(inFront = shown() == CardFace.RECTO) {
                        CardRecto(detail, palette, u, ratio, photoUrl, thumbUrl, soulsById, zone = zone)
                    }
                },
                verso = { bandHeight, measureOnly ->
                    FaceContent(inFront = shown() == CardFace.VERSO) {
                        CardVerso(pebble, palette, u, domains, bandHeight, measureOnly = measureOnly)
                    }
                },
                band = {
                    FaceContent(inFront = shown() == CardFace.VERSO) {
                        CollectionBand(
                            names = pebble.collections.map { it.name },
                            paper = StoneTones.mixOklab(spot, if (colors.isNight) BAND_PAPER_NIGHT else BAND_PAPER, colors.stock),
                            ink = colors.ink,
                            edge = spot,
                        )
                    }
                },
                modifier = areaModifier,
            )
        }
        SpineCardShell(
            palette = palette,
            polarity = pebble.valence.polarity,
            intensity = pebble.valence.intensity,
            modifier =
                Modifier
                    .width(width)
                    .semantics {
                        customActions =
                            listOf(
                                CustomAccessibilityAction(turnAction) {
                                    turn.turn(reduceMotion)
                                    true
                                },
                            )
                    },
        ) {
            Column {
                if (reduceMotion) {
                    Crossfade(
                        targetState = turn.face,
                        modifier = Modifier.turnDrag(turn, reduceMotion = true, rtl = rtl),
                        animationSpec = tween(TURN_FADE_MS),
                        label = "card face",
                    ) { face -> area({ face }, Modifier) }
                } else {
                    area({ turn.front }, Modifier.turnDrag(turn, reduceMotion = false, rtl = rtl).turnLayer(turn, rtl))
                }
                CardFooter(
                    visibility = pebble.visibility,
                    turnTo = turn.front.other().toTarget(),
                    stone = spot,
                    onTurn = { turn.turn(reduceMotion) },
                )
            }
        }
    }
}

/**
 * The face area's turn: [turn]'s progress as a rotation on Y. The end edge lifts toward
 * the eye and swings over to the start edge, as a page turned by its edge does under a
 * finger dragging toward the start (negative degrees in LTR, mirrored in RTL). Past the
 * half the other face is in front, a half-turn on, so its rotation runs back from 90° to
 * 0° and its text reads the right way round. Drawn in the layer alone: no frame of a turn
 * recomposes or re-measures.
 *
 * Perspective makes the near edge taller than the card by `d / (d - w/2 · sin θ)`; the
 * scale `d / (d + w/2 · sin θ)` brings the near edge back to exactly the card's height, so
 * the shell's clip never cuts its corners. `d` is the camera's distance in pixels: HWUI
 * takes `cameraDistance` in Skia's camera units, inches of [SKIA_CAMERA_UNIT] pixels.
 */
private fun Modifier.turnLayer(
    turn: CardTurnState,
    rtl: Boolean,
): Modifier =
    graphicsLayer {
        val t = turn.progress.value
        cameraDistance = TURN_CAMERA_DISTANCE * density
        rotationY = (if (t > TURN_HALF) t - 1f else t) * HALF_TURN_DEG * (if (rtl) 1f else -1f)
        val lean = sin(PI * t).toFloat() * size.width / 2f
        val fit = 1f / (1f + lean / (cameraDistance * SKIA_CAMERA_UNIT))
        scaleX = fit
        scaleY = fit
    }

/**
 * The drag that turns the card (pebble card spec §Behaviour): toward the start edge, in
 * turns of the face area's width ([TurnSettle.progress]); released, it settles by
 * [TurnSettle.target] on the finger's speed in turns a second. A horizontal detector, so
 * the page's vertical scroll keeps every drag that starts vertical. Read outside the
 * rotating layer, so the finger is measured on the page, not on the turned face.
 */
private fun Modifier.turnDrag(
    turn: CardTurnState,
    reduceMotion: Boolean,
    rtl: Boolean,
): Modifier =
    pointerInput(turn, reduceMotion, rtl) {
        val tracker = VelocityTracker()
        val toStart = if (rtl) -1f else 1f
        var dragPx = 0f
        var taken = false
        val width = { size.width.toFloat() }
        detectHorizontalDragGestures(
            onDragStart = {
                tracker.resetTracking()
                dragPx = 0f
                taken = turn.startDrag()
            },
            onDragEnd = {
                if (taken) {
                    val velocity = if (width() > 0f) -tracker.calculateVelocity().x * toStart / width() else 0f
                    turn.release(TurnSettle.progress(dragPx, width()), velocity, reduceMotion)
                }
                taken = false
            },
            onDragCancel = {
                if (taken) turn.cancelDrag()
                taken = false
            },
        ) { change, dragAmount ->
            if (taken) {
                change.consume()
                tracker.addPointerInputChange(change)
                dragPx += dragAmount * toStart
                turn.drag(TurnSettle.progress(dragPx, width()), reduceMotion)
            }
        }
    }

/**
 * One face's content, silent to TalkBack while it is not in front: both faces stay
 * composed, so the hidden one would otherwise still be read. Passes the face area's
 * least size on, which the faces are measured with.
 */
@Composable
private fun FaceContent(
    inFront: Boolean,
    content: @Composable () -> Unit,
) {
    Box(if (inFront) Modifier else Modifier.clearAndSetSemantics {}, propagateMinConstraints = true) { content() }
}

/**
 * The verso's two contents for one band height: the real face, and a probe that
 * measures without painting (`measureOnly`) and says nothing to TalkBack. Rebuilt only
 * when the band's height changes, so `subcompose` is not handed a new lambda every pass.
 */
private class VersoSlots(
    private val verso: @Composable (bandHeight: Dp, measureOnly: Boolean) -> Unit,
) {
    private var bandHeight: Dp? = null
    var face: @Composable () -> Unit = {}
        private set
    var probe: @Composable () -> Unit = {}
        private set

    fun at(height: Dp): VersoSlots {
        if (height != bandHeight) {
            bandHeight = height
            face = { verso(height, false) }
            probe = { Box(Modifier.clearAndSetSemantics {}) { verso(height, true) } }
        }
        return this
    }
}

/**
 * The face slot: both faces composed at one width, the area as tall as the taller, the
 * one in front placed. On the verso the band, measured first so the verso can leave it
 * room, lies over the face's foot, edge to edge.
 *
 * One probe, the verso measured free without its stone painted, gives the verso's own
 * height; the recto is measured once with that as its least height, so its height is the
 * taller of the two, and the verso is measured once at it. Each real face is composed
 * once, so the photo is fetched and the stone painted once.
 *
 * The measure block reads nothing of the turn. Which face is in front ([shown], derived
 * from the turn's progress) is read only while placing, so a turn re-places the area
 * twice, at the half there and back, and never re-measures it; the rotation itself is in
 * the caller's layer.
 */
@Composable
private fun FaceArea(
    shown: () -> CardFace,
    u: Dp,
    recto: @Composable () -> Unit,
    verso: @Composable (bandHeight: Dp, measureOnly: Boolean) -> Unit,
    band: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val slots = remember(verso) { VersoSlots(verso) }
    SubcomposeLayout(modifier) { constraints ->
        val width = constraints.maxWidth
        val bandPlaceable = subcompose(FaceSlot.BAND, band).firstOrNull()?.measure(Constraints.fixedWidth(width))
        val bandHeight = bandPlaceable?.height ?: 0
        val versoSlots = slots.at(bandHeight.toDp())
        // Not intrinsics: the stone's sized aspect box and the faces' custom layouts answer
        // them at the caller's width rather than their own.
        val versoNatural =
            subcompose(FaceSlot.VERSO_PROBE, versoSlots.probe)
                .first()
                .measure(Constraints(minWidth = width, maxWidth = width))
                .height
        val rectoPlaceable =
            subcompose(FaceSlot.RECTO, recto)
                .first()
                .measure(Constraints(minWidth = width, maxWidth = width, minHeight = versoNatural))
        val height = rectoPlaceable.height
        val versoPlaceable = subcompose(FaceSlot.VERSO, versoSlots.face).first().measure(Constraints.fixed(width, height))
        layout(width, height) {
            if (shown() == CardFace.RECTO) {
                rectoPlaceable.place(0, 0)
            } else {
                versoPlaceable.place(0, 0)
                bandPlaceable?.place(0, height - CardLayout.bandFoot(u).roundToPx() - bandHeight)
            }
        }
    }
}

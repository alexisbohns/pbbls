package app.pbbls.android.features.path.card

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.Domain
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.MomentDetail
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.ui.render.stone.BLANK_STONE_PALETTE
import app.pbbls.android.core.ui.render.stone.StoneTones
import app.pbbls.android.core.ui.render.stone.toStonePalette
import app.pbbls.android.features.path.spine.SpineCardShell
import java.time.ZoneId

/** The band's paper: the spot into the stock, 19 % on paper and 24 % at Night (back-token3 `--band`). */
private const val BAND_PAPER = 0.19f
private const val BAND_PAPER_NIGHT = 0.24f

private enum class FaceSlot { RECTO, VERSO, VERSO_PROBE, BAND }

/**
 * The held card (pebble card spec): a still shell, a face that shows the recto or the
 * verso, the collection band wrapped across the face's foot on the verso, and the still
 * footer. Part 1 swaps faces at once; Part 2 animates the turn.
 *
 * Both faces are composed at the card's width and the face area takes the taller, so
 * turning never changes the card's size; the verso fills that height, so its stone
 * centres. Only the showing face is placed.
 *
 * @param detail a moment with a pebble: only a pebble has a card.
 * @param ratio the photo's width / height; null until probed.
 * @param photoUrl the signed original; null until signed.
 * @param thumbUrl the signed thumb, the photo's placeholder.
 * @param soulsById the souls cache, for the recto's marks.
 * @param domains the reference domains, for the postmarks' glyphs.
 * @param initialFace the face showing first: [CardFaceTarget.MOMENT] is the recto.
 * @param zone where the recto's day, time and dateline fall, as `SpineWeek` takes its own.
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
    initialFace: CardFaceTarget = CardFaceTarget.MOMENT,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val pebble = requireNotNull(detail.pebble) { "only a pebble has a card" }
    var face by rememberSaveable { mutableStateOf(initialFace) }
    val colors = PebblesTheme.spine.colors
    val stone = remember(palette) { palette?.toStonePalette() ?: BLANK_STONE_PALETTE }
    val spot = StoneTones.spot(stone, colors.isNight)

    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val width = min(maxWidth - CardLayout.PAGE_MARGIN * 2, CardLayout.MAX_WIDTH).coerceAtLeast(0.dp)
        val u = width / CardLayout.U_DIVISOR
        SpineCardShell(
            palette = palette,
            polarity = pebble.valence.polarity,
            intensity = pebble.valence.intensity,
            modifier = Modifier.width(width),
        ) {
            Column {
                FaceArea(
                    face = face,
                    u = u,
                    recto = { CardRecto(detail, palette, u, ratio, photoUrl, thumbUrl, soulsById, zone = zone) },
                    verso = { bandHeight, measureOnly -> CardVerso(pebble, palette, u, domains, bandHeight, measureOnly = measureOnly) },
                    band = {
                        CollectionBand(
                            names = pebble.collections.map { it.name },
                            paper = StoneTones.mixOklab(spot, if (colors.isNight) BAND_PAPER_NIGHT else BAND_PAPER, colors.stock),
                            ink = colors.ink,
                            edge = spot,
                        )
                    },
                )
                CardFooter(
                    visibility = pebble.visibility,
                    turnTo = if (face == CardFaceTarget.MOMENT) CardFaceTarget.PEBBLE else CardFaceTarget.MOMENT,
                    stone = spot,
                    onTurn = { face = if (face == CardFaceTarget.MOMENT) CardFaceTarget.PEBBLE else CardFaceTarget.MOMENT },
                )
            }
        }
    }
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
 * showing one placed. On the verso the band, measured first so the verso can leave it
 * room, lies over the face's foot, edge to edge.
 *
 * One probe, the verso measured free without its stone painted, gives the verso's own
 * height; the recto is measured once with that as its least height, so its height is the
 * taller of the two, and the verso is measured once at it. Each real face is composed
 * once, so the photo is fetched and the stone painted once.
 *
 * Part 2 places both faces and turns them with `graphicsLayer`. It must read the turn
 * through `derivedStateOf { turn.value > .5f }` for which face is in front, never in this
 * measure block, or every frame of the turn re-measures the card.
 */
@Composable
private fun FaceArea(
    face: CardFaceTarget,
    u: Dp,
    recto: @Composable () -> Unit,
    verso: @Composable (bandHeight: Dp, measureOnly: Boolean) -> Unit,
    band: @Composable () -> Unit,
) {
    val slots = remember(verso) { VersoSlots(verso) }
    SubcomposeLayout { constraints ->
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
            if (face == CardFaceTarget.MOMENT) {
                rectoPlaceable.place(0, 0)
            } else {
                versoPlaceable.place(0, 0)
                bandPlaceable?.place(0, height - CardLayout.bandFoot(u).roundToPx() - bandHeight)
            }
        }
    }
}

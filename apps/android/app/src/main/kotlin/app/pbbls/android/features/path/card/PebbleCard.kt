package app.pbbls.android.features.path.card

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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
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

/** The band's paper: the spot into the stock, 19 % on paper and 24 % at Night (back-token3 `--band`). */
private const val BAND_PAPER = 0.19f
private const val BAND_PAPER_NIGHT = 0.24f

private enum class FaceSlot { RECTO, VERSO, RECTO_PROBE, VERSO_PROBE, BAND }

/**
 * The held card (pebble card spec): a still shell, a face that shows the recto or the
 * verso, the collection band wrapped across the face's foot on the verso, and the still
 * footer. Part 1 swaps faces at once; Part 2 animates the turn.
 *
 * Both faces are measured at the card's width every pass and the face area takes the
 * taller, so turning never changes the card's size; the verso fills that height, so its
 * stone centres. Only the showing face is placed.
 *
 * @param detail a moment with a pebble: only a pebble has a card.
 * @param ratio the photo's width / height; null until probed.
 * @param photoUrl the signed original; null until signed.
 * @param thumbUrl the signed thumb, the photo's placeholder.
 * @param soulsById the souls cache, for the recto's marks.
 * @param domains the reference domains, for the postmarks' glyphs.
 * @param initialFace the face showing first: [CardFaceTarget.MOMENT] is the recto.
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
) {
    val pebble = requireNotNull(detail.pebble) { "only a pebble has a card" }
    var face by rememberSaveable { mutableStateOf(initialFace) }
    val colors = PebblesTheme.spine.colors
    val stone = remember(palette) { palette?.toStonePalette() ?: BLANK_STONE_PALETTE }
    val spot = StoneTones.spot(stone, colors.isNight)

    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val width = min(maxWidth - CardLayout.PAGE_MARGIN * 2, CardLayout.MAX_WIDTH)
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
                    recto = { CardRecto(detail, palette, u, ratio, photoUrl, thumbUrl, soulsById) },
                    verso = { bandHeight -> CardVerso(pebble, palette, u, domains, bandHeight) },
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
 * The face slot: both faces measured at one width, the area as tall as the taller,
 * the showing one placed at that height. On the verso the band, measured first so
 * the verso can leave it room, lies over the face's foot, edge to edge.
 */
@Composable
private fun FaceArea(
    face: CardFaceTarget,
    u: Dp,
    recto: @Composable () -> Unit,
    verso: @Composable (bandHeight: Dp) -> Unit,
    band: @Composable () -> Unit,
) {
    SubcomposeLayout { constraints ->
        val width = constraints.maxWidth
        val bandPlaceable = subcompose(FaceSlot.BAND, band).firstOrNull()?.measure(Constraints.fixedWidth(width))
        val bandHeight = bandPlaceable?.height ?: 0
        val bandDp = bandHeight.toDp()
        // Each face's own height comes from a probe: a copy measured free and never placed.
        // Not intrinsics, which the stone's sized aspect box and the face's custom layouts
        // answer at the caller's width rather than their own.
        val free = Constraints(minWidth = width, maxWidth = width)
        val rectoHeight = subcompose(FaceSlot.RECTO_PROBE, recto).first().measure(free).height
        val versoHeight = subcompose(FaceSlot.VERSO_PROBE) { verso(bandDp) }.first().measure(free).height
        val height = maxOf(rectoHeight, versoHeight)
        val showing =
            if (face == CardFaceTarget.MOMENT) {
                subcompose(FaceSlot.RECTO, recto).first()
            } else {
                subcompose(FaceSlot.VERSO) { verso(bandDp) }.first()
            }
        val placeable = showing.measure(Constraints.fixed(width, height))
        layout(width, height) {
            placeable.place(0, 0)
            if (face == CardFaceTarget.PEBBLE && bandPlaceable != null) {
                bandPlaceable.place(0, height - CardLayout.bandFoot(u).roundToPx() - bandHeight)
            }
        }
    }
}

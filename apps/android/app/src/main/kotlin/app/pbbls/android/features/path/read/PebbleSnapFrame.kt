package app.pbbls.android.features.path.read

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.Valence

/**
 * The snap-present heading of the read view (issue #599): the whole picture at
 * its nearest [BannerAspect] bucket, tilted slightly, with the [PebbleReadPetroglyph]
 * overlapping its top-right corner and tilted the other way. Ports the web
 * `PebbleDetail` snap+pebble bundle.
 *
 * Stateless — the caller resolves the [photo] painter (a decoded snap in the app,
 * a flat colour in previews) and its [aspect], so this whole overlay is
 * screenshot-previewable without a network load. Kept separate from
 * `PebbleReadBanner`'s loader precisely so the same frame can back the edit
 * view's placeholder in the #599 follow-up.
 *
 * The photo is inset from the cluster's top and right edges (the poke insets) so
 * the top-end-aligned Petroglyph pokes out past the corner while overlapping the
 * picture; the tilt is draw-only (`rotate`), so the rounded rectangle turns with
 * the image. The snap is hidden from accessibility (iOS/web parity).
 */
@Composable
fun PebbleSnapFrame(
    photo: Painter,
    aspect: BannerAspect,
    renderSvg: String?,
    valence: Valence,
    palette: EmotionPalette?,
    modifier: Modifier = Modifier,
) {
    val (photoWidth, photoHeight) =
        when (aspect) {
            BannerAspect.SQUARE -> PHOTO_MINOR to PHOTO_MINOR
            BannerAspect.FOUR_THREE -> PHOTO_MAJOR to PHOTO_MINOR
            BannerAspect.THREE_FOUR -> PHOTO_MINOR to PHOTO_MAJOR
        }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // The cluster wraps the photo (plus its poke insets); the Petroglyph
        // aligns to the cluster's top-right so it lands on the photo corner. The
        // insets are asymmetric — it pokes above the photo more than past its
        // right edge, matching the design.
        Box {
            Image(
                painter = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .padding(top = PHOTO_POKE_TOP, end = PHOTO_POKE_END)
                        .size(width = photoWidth, height = photoHeight)
                        .rotate(PHOTO_TILT)
                        .clip(RoundedCornerShape(PHOTO_CORNER)),
            )
            PebbleReadPetroglyph(
                renderSvg = renderSvg,
                valence = valence,
                palette = palette,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(OVERLAY_PETROGLYPH)
                        .rotate(PETROGLYPH_TILT),
            )
        }
    }
}

/** Shorter photo edge; the longer edge grows to [PHOTO_MAJOR] for non-square snaps. */
private val PHOTO_MINOR = 150.dp

/** Longer photo edge for 4:3 / 3:4 snaps (150 × 4/3). */
private val PHOTO_MAJOR = 200.dp

/** Top inset of the photo — how far the Petroglyph pokes above it. */
private val PHOTO_POKE_TOP = 40.dp

/** Right inset of the photo — how far the Petroglyph pokes past its right edge. */
private val PHOTO_POKE_END = 28.dp

/** Layout slot for the overlapping Petroglyph (its tilt bounding runs a touch larger). */
private val OVERLAY_PETROGLYPH = 96.dp

private val PHOTO_CORNER = 18.dp

/** Counter-clockwise photo tilt, clockwise Petroglyph tilt — the web `-rotate-4` / `rotate-7`. */
private const val PHOTO_TILT = -5f
private const val PETROGLYPH_TILT = 7.5f

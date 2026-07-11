package app.pbbls.android.features.path.render

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.features.path.models.PebbleFrameColors
import app.pbbls.android.theme.PebblesTheme

/**
 * The row's pebble artwork: outline silhouette backdrop + (when present) the
 * server-composed render on top, down-scaled per [PebbleOutlineGeometry] —
 * ports the `thumbnail` stack of iOS `PathPebbleRow`. Colors come from
 * [EmotionPalette.pebbleFrameColors]; a missing palette (cache cold, unknown
 * emotion, no emotion) falls back to the brand accent, mirroring iOS.
 *
 * [palette] is a parameter (not a service read) so screenshot previews can
 * drive the composable without a live client.
 */
@Composable
fun PebbleThumbnail(
    pebble: Pebble,
    palette: EmotionPalette?,
    modifier: Modifier = Modifier,
) {
    val accentHex = PebblesTheme.colors.accent.primaryHex
    val frame =
        palette?.pebbleFrameColors(pebble.intensity)
            ?: PebbleFrameColors(strokeHex = accentHex, fillHex = accentHex, fillOpacity = 1f)
    val sizeGroup = pebble.valence.sizeGroup

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.aspectRatio(PebbleOutlineGeometry.aspectRatio(sizeGroup)),
            contentAlignment = Alignment.Center,
        ) {
            PebbleOutlineBackdrop(
                sizeGroup = sizeGroup,
                polarity = pebble.valence.polarity,
                fillHex = frame.fillHex,
                fillOpacity = frame.fillOpacity,
                modifier = Modifier.fillMaxSize(),
            )
            if (pebble.renderSvg != null) {
                PebbleSvg(
                    svg = pebble.renderSvg,
                    strokeHex = frame.strokeHex,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .scale(PebbleOutlineGeometry.pebbleScale(sizeGroup)),
                )
            }
        }
    }
}

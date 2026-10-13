package app.pbbls.android.core.ui.render

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.pbbls.android.core.designsystem.toRgbHex
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.PebbleFrameColors
import app.pbbls.android.core.model.Valence

/**
 * The row's pebble artwork: the outline silhouette backdrop for [valence] —
 * the `thumbnail` stack of iOS `PathPebbleRow`. The server's composed
 * `render_svg` is gone with the moments split (#1048); the glyph drawn from
 * strokes returns on top of this backdrop in the next part. Colors come from
 * [EmotionPalette.pebbleFrameColors]; a missing palette (cache cold, unknown
 * emotion, no emotion) falls back to the brand accent, mirroring iOS.
 *
 * [palette] is a parameter (not a service read) so screenshot previews can
 * drive the composable without a live client.
 */
@Composable
fun PebbleThumbnail(
    valence: Valence,
    palette: EmotionPalette?,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val accentHex = remember(primary) { primary.toRgbHex() }
    val frame =
        palette?.pebbleFrameColors(valence.intensity)
            ?: PebbleFrameColors(strokeHex = accentHex, fillHex = accentHex, fillOpacity = 1f)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        PebbleOutlineBackdrop(
            sizeGroup = valence.sizeGroup,
            polarity = valence.polarity,
            fillHex = frame.fillHex,
            fillOpacity = frame.fillOpacity,
            modifier = Modifier.aspectRatio(PebbleOutlineGeometry.aspectRatio(valence.sizeGroup)),
        )
    }
}

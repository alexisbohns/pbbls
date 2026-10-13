package app.pbbls.android.core.ui.render

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.pbbls.android.core.designsystem.toRgbHex
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.PebbleFrameColors
import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.model.Valence

/**
 * The row's pebble artwork: the outline silhouette backdrop for [valence] with
 * the composed stone (shape seed + derived [glyph]) traced on top — the
 * `thumbnail` stack of iOS `PathPebbleRow`. A null [glyph] draws the stone
 * without one. Colors come from [EmotionPalette.pebbleFrameColors]; a missing
 * palette (cache cold, unknown emotion, no emotion) falls back to the brand
 * accent, mirroring iOS.
 *
 * [palette] is a parameter (not a service read) so screenshot previews can
 * drive the composable without a live client.
 */
@Composable
fun PebbleThumbnail(
    valence: Valence,
    glyph: PebbleGlyph?,
    palette: EmotionPalette?,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val accentHex = remember(primary) { primary.toRgbHex() }
    val frame =
        palette?.pebbleFrameColors(valence.intensity)
            ?: PebbleFrameColors(strokeHex = accentHex, fillHex = accentHex, fillOpacity = 1f)
    PebbleStone(valence = valence, glyph = glyph, colors = frame, modifier = modifier)
}

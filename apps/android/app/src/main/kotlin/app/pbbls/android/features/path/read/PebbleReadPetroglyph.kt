package app.pbbls.android.features.path.read

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.pbbls.android.core.designsystem.toRgbHex
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.PebbleFrameColors
import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.ui.render.PebbleOutlineGeometry
import app.pbbls.android.core.ui.render.PebbleStone

/**
 * The read-view "Petroglyph" (issue #599): the outline silhouette backfill with
 * the composed stone (shape seed + derived glyph) traced on top, down-scaled per
 * [PebbleOutlineGeometry] to sit inside the silhouette. Same [PebbleStone] the
 * Path rows draw via `PebbleThumbnail`, but coloured through [petroglyphColors]
 * so the read page follows #599's per-size, per-theme table instead of the
 * shared theme-neutral `pebbleFrameColors`. A null [glyph] draws the stone
 * without one.
 *
 * Sized by the caller's [modifier]: the heading treatment gives it a large
 * square slot, the snap overlay a smaller one. A missing [palette] (cache cold,
 * unknown or absent emotion) falls back to the theme's `primary`, mirroring
 * `PebbleThumbnail`. [palette] and the valence data arrive as parameters so
 * screenshot previews drive it without a live client.
 */
@Composable
fun PebbleReadPetroglyph(
    valence: Valence,
    glyph: PebbleGlyph?,
    palette: EmotionPalette?,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val accentHex = remember(primary) { primary.toRgbHex() }
    val colors =
        palette?.let { petroglyphColors(it, valence.sizeGroup, isSystemInDarkTheme()) }
            ?: PetroglyphColors(strokeHex = accentHex, fillHex = accentHex, fillOpacity = 1f)
    PebbleStone(
        valence = valence,
        glyph = glyph,
        colors = PebbleFrameColors(colors.strokeHex, colors.fillHex, colors.fillOpacity),
        modifier = modifier,
    )
}

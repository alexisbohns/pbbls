package app.pbbls.android.features.path.read

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.Valence
import app.pbbls.android.features.path.render.PebbleOutlineBackdrop
import app.pbbls.android.features.path.render.PebbleOutlineGeometry
import app.pbbls.android.features.path.render.PebbleStaticRender
import app.pbbls.android.theme.PebblesTheme

/**
 * The read-view "Petroglyph" (issue #599): the outline silhouette backfill with
 * the server-composed render (outline + glyph) traced on top, down-scaled per
 * [PebbleOutlineGeometry] to sit inside the silhouette. Same composite the Path
 * rows draw via `PebbleThumbnail`, but coloured through [petroglyphColors] so
 * the read page follows #599's per-size, per-theme table instead of the shared
 * theme-neutral `pebbleFrameColors`.
 *
 * Sized by the caller's [modifier]: the heading treatment gives it a large
 * square slot, the snap overlay a smaller one. A missing [palette] (cache cold,
 * unknown or absent emotion) falls back to the brand accent, mirroring
 * `PebbleThumbnail`. [palette] and the valence data arrive as parameters so
 * screenshot previews drive it without a live client.
 */
@Composable
fun PebbleReadPetroglyph(
    renderSvg: String?,
    valence: Valence,
    palette: EmotionPalette?,
    modifier: Modifier = Modifier,
) {
    val accentHex = PebblesTheme.colors.accent.primaryHex
    val sizeGroup = valence.sizeGroup
    val colors =
        palette?.let { petroglyphColors(it, sizeGroup, isSystemInDarkTheme()) }
            ?: PetroglyphColors(strokeHex = accentHex, fillHex = accentHex, fillOpacity = 1f)

    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.aspectRatio(PebbleOutlineGeometry.aspectRatio(sizeGroup)),
            contentAlignment = Alignment.Center,
        ) {
            PebbleOutlineBackdrop(
                sizeGroup = sizeGroup,
                polarity = valence.polarity,
                fillHex = colors.fillHex,
                fillOpacity = colors.fillOpacity,
                modifier = Modifier.fillMaxSize(),
            )
            if (renderSvg != null) {
                PebbleStaticRender(
                    svg = renderSvg,
                    strokeHex = colors.strokeHex,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .scale(PebbleOutlineGeometry.pebbleScale(sizeGroup)),
                )
            }
        }
    }
}

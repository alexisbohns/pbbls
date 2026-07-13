package app.pbbls.android.features.path.read

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.Valence
import app.pbbls.android.features.path.models.ValenceSizeGroup
import app.pbbls.android.features.path.render.PebbleStaticRender
import app.pbbls.android.theme.PebblesTheme

/**
 * Render banner for the detail sheet — ports iOS `PebbleReadBanner.swift`'s
 * `renderedPebble`, minus the photo reveal (snaps are a non-goal this
 * milestone). Draws the bare `render_svg` through the existing [PebbleSvg], the
 * pebble sized by valence within a 120dp min-height zone. The palette stroke is
 * SVG-safe 6-digit hex; without a [palette] it falls back to the brand accent.
 */
@Composable
fun PebbleReadBanner(
    renderSvg: String?,
    valence: Valence,
    palette: EmotionPalette?,
    modifier: Modifier = Modifier,
) {
    val accentHex = PebblesTheme.colors.accent.primaryHex
    val strokeHex = palette?.pebbleFrameColors(valence.intensity)?.strokeHex ?: accentHex
    val heightDp =
        when (valence.sizeGroup) {
            ValenceSizeGroup.SMALL -> 80.dp
            ValenceSizeGroup.MEDIUM -> 100.dp
            ValenceSizeGroup.LARGE -> 116.dp
        }
    Box(modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
        if (renderSvg != null) {
            // PebbleStaticRender fit-scales + centers, so a wide/short box lets
            // height dominate: the pebble sits at heightDp, centered. Tracing at
            // the outline weight keeps the glyph consistent with the outline
            // (and with the Path row), matching custom and domain glyphs.
            PebbleStaticRender(
                svg = renderSvg,
                strokeHex = strokeHex,
                modifier = Modifier.fillMaxWidth().height(heightDp),
            )
        }
    }
}

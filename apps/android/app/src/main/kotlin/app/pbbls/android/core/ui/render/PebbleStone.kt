package app.pbbls.android.core.ui.render

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.pbbls.android.R
import app.pbbls.android.core.model.BlankPalette
import app.pbbls.android.core.model.PebbleFrameColors
import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.model.Valence

/**
 * A stone: the outline silhouette filled with [colors], and the composed
 * shape + derived glyph traced on top at one weight, down-scaled per
 * [PebbleOutlineGeometry] as the server render used to be.
 */
@Composable
fun PebbleStone(
    valence: Valence,
    glyph: PebbleGlyph?,
    colors: PebbleFrameColors,
    modifier: Modifier = Modifier,
) {
    StoneLayers(
        valence = valence,
        colors = colors,
        modifier = modifier,
    ) { scale ->
        val model = rememberStoneModel(valence, glyph) ?: return@StoneLayers
        PebbleStaticRender(
            model = model,
            cacheKey = StoneComposer.cacheKey(valence.sizeGroup, valence.polarity, glyph),
            strokeHex = colors.strokeHex,
            modifier = Modifier.fillMaxSize().scale(scale),
        )
    }
}

/**
 * An uncompleted moment (R4): the medium neutral silhouette in the fixed blank
 * greys, with nothing carved: no glyph, no veins, and no outline stroke on top
 * (an extra outline reads as a heavy, offset double contour; iOS and the web
 * `LitStone` blank branch draw the silhouette alone). [colors] defaults to the
 * Path-row mapping; the read page passes its Petroglyph mapping of the same ramp.
 */
@Composable
fun BlankStone(
    modifier: Modifier = Modifier,
    colors: PebbleFrameColors = BlankPalette.pebbleFrameColors(intensity = 2),
) {
    val label = stringResource(R.string.moment_blank_a11y)
    StoneLayers(
        valence = Valence.NEUTRAL_MEDIUM,
        colors = colors,
        modifier = modifier.semantics { contentDescription = label },
    )
}

/** The filled silhouette, with [carving] traced on top when there is one. */
@Composable
private fun StoneLayers(
    valence: Valence,
    colors: PebbleFrameColors,
    modifier: Modifier,
    carving: (@Composable (scale: Float) -> Unit)? = null,
) {
    val sizeGroup = valence.sizeGroup
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
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
            carving?.invoke(PebbleOutlineGeometry.pebbleScale(sizeGroup))
        }
    }
}

package app.pbbls.android.features.path.valence

import android.content.Context
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import app.pbbls.android.features.path.models.Valence
import app.pbbls.android.features.path.render.Affine
import app.pbbls.android.features.path.render.OutlineAssets
import app.pbbls.android.features.path.render.PebbleOutlineGeometry
import app.pbbls.android.features.path.render.PebbleSvgModel
import app.pbbls.android.features.path.render.wobble.WobbleRenderer
import app.pbbls.android.features.path.render.wobble.wobbleInkPath
import app.pbbls.android.theme.PebblesTheme

private const val TAG = "ValenceStone"

/** Long enough to read as a change of state, short enough to keep up with the roll's detents. */
private const val SELECTION_FADE_MS = 220

/**
 * One valence stone, composed the way the Path and the read sheet compose a
 * real pebble: a soft-filled silhouette behind, the artwork inked inside it —
 * ports iOS `ValenceStoneView.swift`.
 *
 * The backdrop is the wobbled `outline_<size>_<polarity>.svg` shape, filled and
 * never stroked. The artwork on top is the wobbled `valence_art_*` ink (the
 * pebble's own outline plus its creature and fossil), tinted and scaled down by
 * [PebbleOutlineGeometry.pebbleScale] so the backdrop frames it with the same
 * ~12% margin a real stone gets — instead of the backdrop's edge and the
 * artwork's edge landing on top of each other.
 *
 * Both halves go through the same renderer a real pebble does, and both are
 * memoized there, so nine stones cost nine parses once per process — never per
 * frame. The paths are transformed into the stone's own box at build time
 * rather than by transforming the canvas: a `ShaderBrush` is sized from the
 * draw scope, which a canvas transform does not change, so the highlight
 * gradient would be scaled and offset out of the stone.
 *
 * Selection **crossfades two fixed layers** rather than animating one changing
 * fill. The resting and selected fills are not even the same kind of thing — in
 * dark mode a flat colour gives way to a baked mesh — and no framework can
 * interpolate between those two, so each layer here keeps one brush for its
 * whole life and only its alpha moves.
 *
 * Deliberately **not** gated on `WobbleFlags`: unlike the Path row or the form
 * glyph there is no smooth variant of this to fall back to — a stone *is* a
 * wobbled silhouette with wobbled ink inside it, and the flag's AndroidSVG
 * branch draws a single-tint shape that cannot carry the two roles. Every
 * shipped build has the flag on anyway (`android-release.yml` sets it), so the
 * only builds where the fan and the Path rows disagree are local release ones.
 *
 * Knows nothing about selection order or placement: the fan owns both.
 */
@Composable
internal fun ValenceStone(
    valence: Valence,
    /** On-screen height of the whole stone, backdrop included. */
    height: Dp,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val size = valence.sizeGroup
    val width = height * PebbleOutlineGeometry.aspectRatio(size)
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = PebblesTheme.colors
    val isDark = isSystemInDarkTheme()

    val assetKey = valenceAssetKey(valence)
    val backdrop =
        remember(assetKey) {
            WobbleRenderer
                .backdropArt(assetKey, readRawText(context, OutlineAssets.resId(size, valence.polarity)))
                .also { if (it == null) Log.e(TAG, "unparseable outline asset: $assetKey") }
        }
    val artwork =
        remember(assetKey) {
            ValenceArt
                .art(assetKey, readRawText(context, ValenceArtAssets.resId(size, valence.polarity)))
                .also { if (it == null) Log.e(TAG, "unparseable valence artwork: $assetKey") }
        }

    val widthPx = with(density) { width.toPx() }
    val heightPx = with(density) { height.toPx() }

    val backdropPath =
        remember(backdrop, widthPx, heightPx) {
            backdrop?.let {
                wobbleInkPath(
                    contours = it.contours,
                    transform = fitTransform(it.viewBox, widthPx, heightPx),
                    fillType = if (it.usesEvenOddFill) PathFillType.EvenOdd else PathFillType.NonZero,
                )
            }
        }
    val artPaths =
        remember(artwork, widthPx, heightPx) {
            artwork
                ?.let { art ->
                    // The artwork is fitted into a box scaled down by `pebbleScale`
                    // and centred in the stone, so the backdrop frames it.
                    val scale = PebbleOutlineGeometry.pebbleScale(size)
                    val transform = fitTransform(art.viewBox, widthPx * scale, heightPx * scale, widthPx, heightPx)
                    buildList {
                        if (art.ink.isNotEmpty()) add(wobbleInkPath(art.ink, transform))
                        art.regions.forEach { region ->
                            add(
                                wobbleInkPath(
                                    contours = region.contours,
                                    transform = transform,
                                    fillType =
                                        if (region.usesEvenOddFill) PathFillType.EvenOdd else PathFillType.NonZero,
                                ),
                            )
                        }
                    }
                }.orEmpty()
        }

    val resting =
        ValenceStoneStyles.style(
            polarity = valence.polarity,
            isSelected = false,
            isDark = isDark,
            system = colors.system,
            accent = colors.accent,
        )
    val selected =
        ValenceStoneStyles.style(
            polarity = valence.polarity,
            isSelected = true,
            isDark = isDark,
            system = colors.system,
            accent = colors.accent,
        )
    // The crossfade itself: one number, and the only thing about the fill that
    // ever moves.
    val selectedAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(durationMillis = SELECTION_FADE_MS),
        label = "valenceStoneSelection",
    )

    Canvas(modifier = modifier.size(width, height)) {
        // Four draws instead of two, which costs nothing: the paths are
        // memoized and the brushes are the same ones either way.
        drawStone(resting, 1f - selectedAlpha, backdropPath, artPaths)
        drawStone(selected, selectedAlpha, backdropPath, artPaths)
    }
}

private fun DrawScope.drawStone(
    style: ValenceStoneStyle,
    alpha: Float,
    backdrop: Path?,
    artwork: List<Path>,
) {
    if (alpha <= 0f) return
    backdrop?.let { drawPath(it, style.backdrop, alpha = alpha * style.backdropAlpha) }
    artwork.forEach { drawPath(it, style.ink, alpha = alpha) }
}

/**
 * Cache key for one stone's two assets, shared with `prewarmValenceStones` so
 * the warm-up and the draw hit the same entries.
 */
internal fun valenceAssetKey(valence: Valence): String = "${valence.sizeGroup.key}-${valence.polarity.key}"

/** Reads a bundled `res/raw` asset as text. JVM code cannot reach resources, so the caller does. */
internal fun readRawText(
    context: Context,
    resId: Int,
): String =
    context.resources
        .openRawResource(resId)
        .bufferedReader()
        .use { it.readText() }

/**
 * Aspect-fits [viewBox] into a [boxWidth] × [boxHeight] rectangle centred in a
 * [containerWidth] × [containerHeight] one — the same fit + centring
 * `SvgCanvas` and `PebbleOutlineBackdrop` apply on the canvas. Defaults make
 * the box the container, which is the plain fit.
 */
private fun fitTransform(
    viewBox: PebbleSvgModel.ViewBox,
    boxWidth: Float,
    boxHeight: Float,
    containerWidth: Float = boxWidth,
    containerHeight: Float = boxHeight,
): Affine {
    if (viewBox.width <= 0f || viewBox.height <= 0f) return Affine.IDENTITY
    val fit = minOf(boxWidth / viewBox.width, boxHeight / viewBox.height)
    return Affine(
        a = fit,
        b = 0f,
        c = 0f,
        d = fit,
        e = (containerWidth - viewBox.width * fit) / 2f - viewBox.minX * fit,
        f = (containerHeight - viewBox.height * fit) / 2f - viewBox.minY * fit,
    )
}

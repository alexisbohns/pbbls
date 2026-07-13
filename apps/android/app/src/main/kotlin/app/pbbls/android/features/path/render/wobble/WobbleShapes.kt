package app.pbbls.android.features.path.render.wobble

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import app.pbbls.android.features.path.render.Affine

/*
 * Compose-side conversion of the pure wobble geometry — the Android analog of
 * iOS `WobbleShapes.swift`. Kept apart from the pure pipeline so everything
 * else stays JVM unit-testable, and apart from the render composables so the
 * module deletes cleanly.
 *
 * iOS `WobbleMask` (the appear animation's reveal-mask geometry) has no
 * counterpart here yet: Android has no draw-on animation. When one lands it
 * must reveal the filled ink with a fat trimmed stroke along
 * `WobbleArt.centerline` (the iOS `PebbleAnimatedRenderView` approach) — a
 * path trim cannot animate a fill directly.
 */

/**
 * Builds one filled Compose [Path] from wobbled ink contours, with
 * [transform] (the owning layer's affine, identity elsewhere) baked in. The
 * default [PathFillType.NonZero] is what makes a closed ring's
 * opposite-winding contour pair render as an annulus.
 */
internal fun wobbleInkPath(
    contours: List<List<WobblePoint>>,
    transform: Affine = Affine.IDENTITY,
    fillType: PathFillType = PathFillType.NonZero,
): Path {
    val path = Path()
    path.fillType = fillType
    for (contour in contours) {
        contour.forEachIndexed { index, point ->
            val x = (transform.a * point.x + transform.c * point.y + transform.e).toFloat()
            val y = (transform.b * point.x + transform.d * point.y + transform.f).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }
    return path
}

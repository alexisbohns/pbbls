package app.pbbls.android.features.path.render

/**
 * Pure string substitutions applied to SVG markup *before* parsing — the
 * Android analog of iOS `PebbleRenderView.coloredSvg` and
 * `PebbleOutlineBackdropView`. Both operate on raw markup because the color
 * arrives per-pebble at render time while the SVG text comes from the server
 * (`render_svg`) or a bundled asset (the outline silhouettes).
 *
 * Injected hex values must be 6-digit `#RRGGBB` — SVG engines misparse the
 * 8-digit `#RRGGBBAA` form stored on `emotion_categories`, which is why
 * `EmotionPalette` truncates before anything reaches these functions (same
 * contract as iOS `EmotionPalette.rgbHex`).
 */
object SvgColors {
    /** Sentinel fill baked into the bundled outline SVGs (issue #473 assets). */
    const val OUTLINE_FILL_SENTINEL = "#FF00FF"

    /**
     * Replaces every literal `currentColor` in a server-composed pebble SVG
     * with the palette stroke hex. The compositor authors all strokes as
     * `stroke="currentColor"` precisely so clients can inject the emotion
     * color this way.
     */
    fun injectStrokeColor(
        svg: String,
        strokeHex: String,
    ): String = svg.replace("currentColor", strokeHex)

    /**
     * Replaces the `#FF00FF` sentinel fill in a bundled outline silhouette
     * with the palette fill hex. Fill alpha cannot ride along in a 6-digit
     * hex — callers apply it separately as view alpha (`fillOpacity`).
     */
    fun injectOutlineFill(
        svg: String,
        fillHex: String,
    ): String = svg.replace(OUTLINE_FILL_SENTINEL, fillHex)
}

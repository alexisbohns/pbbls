package app.pbbls.android.features.path.render

/**
 * Stroke geometry for the pebble layer-tracing renderer ([PebbleStaticRender]).
 *
 * The server-composed pebble SVG authors the outline (`layer:shape`) at
 * `stroke-width="6"` in viewBox units, but each glyph is authored at its own
 * width: a custom carved glyph is normalized to `strokeWidth × fitScale` (often
 * heavier), while a domain glyph carries the icon's authored width (often
 * lighter). AndroidSVG honors those per-layer widths, so on the Path and detail
 * pages custom glyphs render thicker than domain glyphs.
 *
 * Tracing every layer at the outline's weight makes glyph == outline
 * everywhere, so custom and domain glyphs read identically — the Android analog
 * of the iOS fix in PR #511.
 */
internal object PebbleStroke {
    /**
     * Outline stroke width in viewBox units — the weight every layer is traced
     * at. Mirrors the `6` on every shape path in the engine's shape templates
     * (and iOS `PebbleStroke.outlineWidth`). The renderer scales the whole
     * viewBox to the frame, so the on-screen weight is `OUTLINE_WIDTH × fitScale`
     * — exactly today's outline weight, now shared by the glyph.
     */
    const val OUTLINE_WIDTH: Float = 6f
}

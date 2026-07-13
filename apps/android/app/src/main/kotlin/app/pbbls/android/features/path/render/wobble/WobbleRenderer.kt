package app.pbbls.android.features.path.render.wobble

import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import app.pbbls.android.features.path.render.PebbleStroke
import app.pbbls.android.features.path.render.PebbleSvgModel

/**
 * Per-layer wobbled artwork for a composed pebble SVG, index-aligned with
 * [PebbleSvgModel.layers].
 */
internal class WobblePebbleArt(
    val layers: List<WobbleArt>,
)

/**
 * Wobbled backdrop silhouette, in its asset's viewBox space. [contours] are
 * closed fill contours (see [WobbleArt.ink]).
 */
internal class WobbleBackdropArt(
    val contours: List<List<WobblePoint>>,
    val viewBox: PebbleSvgModel.ViewBox,
    /**
     * `outline_large_lowlight.svg` carves a hole with `fill-rule="evenodd"`;
     * the other eight assets fill nonzero.
     */
    val usesEvenOddFill: Boolean,
)

/**
 * Entry point of the wobble experiment — mirrors iOS `WobbleRenderer.swift`:
 * derives per-surface parameters (issue #555 §2.1 spaces and half-widths),
 * runs flatten → outline → displace, and caches results so the cost is paid
 * once per artwork — never per frame.
 *
 * Pure JVM (no Compose/`android.graphics` types beyond the [PathNode]
 * parser, which is common-Kotlin), so the whole surface unit-tests without
 * Robolectric. Deliberately log-free for the same reason (`android.util.Log`
 * throws off-device — same rule as `Valence`); parse failures degrade to
 * `null`/skips and the calling composable's fallback branch logs.
 */
internal object WobbleRenderer {
    /** One noise field for the whole app: the static look is seed 3 (§1). */
    private val noise = SVGTurbulence(WobbleParams.SEED)

    // Keys are content strings: collision-proof, and a few KB per key is
    // negligible next to the cached geometry. Count-bounded LRU — the NSCache
    // analog minus the memory-pressure hook.
    private val pebbleCache = LruCache<WobblePebbleArt>(maxSize = 128)
    private val glyphCache = LruCache<WobbleArt>(maxSize = 512)
    private val backdropCache = LruCache<WobbleBackdropArt>(maxSize = 16) // 9 assets exist

    // ── Pebble render (layer:shape / layer:fossil / layer:glyph) ──

    fun pebbleArt(
        svg: String,
        model: PebbleSvgModel,
    ): WobblePebbleArt {
        pebbleCache.get(svg)?.let { return it }
        val canvasParams = WobbleParams.scaled(model.viewBox.width.toDouble(), model.viewBox.height.toDouble())
        val layers =
            model.layers.map { layer ->
                val params: WobbleParams
                val halfWidth: Double
                if (layer.kind == PebbleSvgModel.Layer.Kind.GLYPH && layer.transform.a > 0.01f) {
                    // Glyph paths live in the engine's 200-box slot space; the
                    // layer transform scales them onto the canvas. Wobble in the
                    // raw box with canonical params, and pre-divide the half-width
                    // so the ink lands at the outline's weight after the
                    // transform (#509 uniform-weight rule).
                    params = WobbleParams.CANONICAL
                    halfWidth = PebbleStroke.OUTLINE_WIDTH.toDouble() / layer.transform.a.toDouble() / 2
                } else {
                    // Shape and fossil are authored in canvas space. A glyph layer
                    // with a degenerate transform (never emitted by the engine)
                    // also degrades to this treatment rather than dividing by ~0.
                    params = canvasParams
                    halfWidth = PebbleStroke.OUTLINE_WIDTH.toDouble() / 2
                }
                val polylines =
                    layer.paths.flatMap { spec ->
                        WobblePathFlattener.flatten(parseNodes(spec.d), params.flattenStep, spec.transform)
                    }
                WobbleOutlineBuilder.art(polylines, halfWidth, params, noise)
            }
        val art = WobblePebbleArt(layers)
        pebbleCache.put(svg, art)
        return art
    }

    // ── Backdrop silhouettes (bundled res/raw assets) ──────────

    /**
     * Wobbled silhouette for one outline asset. [assetName] is the cache key
     * (`"{size}-{polarity}"`); [raw] is the asset's markup, loaded by the
     * composable (JVM code can't reach Android resources). Returns null when
     * the markup can't be parsed — the caller falls back to AndroidSVG.
     */
    fun backdropArt(
        assetName: String,
        raw: String,
    ): WobbleBackdropArt? {
        backdropCache.get(assetName)?.let { return it }
        val art = backdropArt(raw) ?: return null
        backdropCache.put(assetName, art)
        return art
    }

    /**
     * Parses one outline asset — a single filled `<path>`, the verified shape
     * of all nine bundled files — and displaces its contours. No outline
     * building: the silhouette is already a fill region, so wobbling its edge
     * is the whole effect.
     */
    fun backdropArt(raw: String): WobbleBackdropArt? {
        val viewBoxAttribute = attribute("viewBox", raw) ?: return null
        val viewBox = parseViewBox(viewBoxAttribute) ?: return null
        if (viewBox.width <= 0f || viewBox.height <= 0f) return null
        val pathData = attribute("d", raw) ?: return null
        val nodes = parseNodes(pathData)
        if (nodes.isEmpty()) return null

        val params = WobbleParams.scaled(viewBox.width.toDouble(), viewBox.height.toDouble())
        val contours =
            WobblePathFlattener.flatten(nodes, params.flattenStep).mapNotNull { polyline ->
                val displaced = polyline.points.map { params.displace(it, noise) }
                // Silhouette contours are fills — treated as closed whether or
                // not the asset spelled out the trailing `Z`.
                if (displaced.size > 2) displaced else null
            }
        if (contours.isEmpty()) return null
        return WobbleBackdropArt(
            contours = contours,
            viewBox = viewBox,
            usesEvenOddFill = raw.contains("fill-rule=\"evenodd\""),
        )
    }

    // ── Glyph strokes (souls cells, pills, picker, form row) ───

    /**
     * Wobbled filled ink for one raw glyph stroke in the 200-box space.
     * Returns null when the `d` string doesn't parse (caller falls back to
     * the plain stroke).
     */
    fun glyphInk(
        d: String,
        width: Double,
    ): List<List<WobblePoint>>? {
        val key = "$width|$d"
        glyphCache.get(key)?.let { return it.ink }
        val nodes = parseNodes(d)
        if (nodes.isEmpty()) return null
        val params = WobbleParams.CANONICAL
        val polylines = WobblePathFlattener.flatten(nodes, params.flattenStep)
        if (polylines.isEmpty()) return null
        val art = WobbleOutlineBuilder.art(polylines, width / 2, params, noise)
        glyphCache.put(key, art)
        return art.ink
    }

    // ── Minimal asset scanning ─────────────────────────────────

    /** A malformed `d` is a data condition — empty list, caller degrades. */
    private fun parseNodes(d: String): List<PathNode> =
        try {
            PathParser().parsePathString(d).toNodes()
        } catch (e: Exception) {
            emptyList()
        }

    /**
     * First `name="…"` attribute value in [svg]. The word boundary keeps
     * `d=` from matching `id=`.
     */
    private fun attribute(
        name: String,
        svg: String,
    ): String? = Regex("(?<![\\w-])${Regex.escape(name)}=\"([^\"]*)\"").find(svg)?.groupValues?.get(1)

    private fun parseViewBox(value: String): PebbleSvgModel.ViewBox? {
        val parts = value.trim().split(Regex("[,\\s]+")).mapNotNull { it.toFloatOrNull() }
        if (parts.size != 4) return null
        return PebbleSvgModel.ViewBox(parts[0], parts[1], parts[2], parts[3])
    }
}

/** Tiny synchronized count-bounded LRU (access order) — the NSCache analog. */
private class LruCache<V>(
    maxSize: Int,
) {
    private val map =
        object : LinkedHashMap<String, V>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>): Boolean = size > maxSize
        }

    @Synchronized
    fun get(key: String): V? = map[key]

    @Synchronized
    fun put(
        key: String,
        value: V,
    ) {
        map[key] = value
    }
}

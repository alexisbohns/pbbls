package app.pbbls.android.features.path.valence

import app.pbbls.android.R
import app.pbbls.android.features.path.models.ValencePolarity
import app.pbbls.android.features.path.models.ValenceSizeGroup
import app.pbbls.android.features.path.render.PebbleSvgModel
import app.pbbls.android.features.path.render.wobble.WobblePoint
import app.pbbls.android.features.path.render.wobble.WobbleRenderer

/**
 * (size, polarity) → raw artwork resource — compile-checked and greppable,
 * like `OutlineAssets` and `ValenceAssets`.
 *
 * The nine files are byte-identical copies of
 * `apps/ios/Pebbles/Resources/ValenceArt/valence-<polarity><Size>.svg`,
 * renamed to Android's `[a-z0-9_]` resource alphabet (rename map in
 * `apps/android/CLAUDE.md`). They are **not** the older
 * `res/raw/valence_<size>_<polarity>.svg` line art the form row still uses:
 * those came from the web SVGs, these are generated from the source PDFs by
 * `apps/ios/Scripts/valence-art-to-svg.mjs` and carry the stroke widths the
 * PDF drew with.
 */
object ValenceArtAssets {
    private val ids: Map<Pair<ValenceSizeGroup, ValencePolarity>, Int> =
        mapOf(
            (ValenceSizeGroup.SMALL to ValencePolarity.LOWLIGHT) to R.raw.valence_art_small_lowlight,
            (ValenceSizeGroup.SMALL to ValencePolarity.NEUTRAL) to R.raw.valence_art_small_neutral,
            (ValenceSizeGroup.SMALL to ValencePolarity.HIGHLIGHT) to R.raw.valence_art_small_highlight,
            (ValenceSizeGroup.MEDIUM to ValencePolarity.LOWLIGHT) to R.raw.valence_art_medium_lowlight,
            (ValenceSizeGroup.MEDIUM to ValencePolarity.NEUTRAL) to R.raw.valence_art_medium_neutral,
            (ValenceSizeGroup.MEDIUM to ValencePolarity.HIGHLIGHT) to R.raw.valence_art_medium_highlight,
            (ValenceSizeGroup.LARGE to ValencePolarity.LOWLIGHT) to R.raw.valence_art_large_lowlight,
            (ValenceSizeGroup.LARGE to ValencePolarity.NEUTRAL) to R.raw.valence_art_large_neutral,
            (ValenceSizeGroup.LARGE to ValencePolarity.HIGHLIGHT) to R.raw.valence_art_large_highlight,
        )

    fun resId(
        size: ValenceSizeGroup,
        polarity: ValencePolarity,
    ): Int = ids.getValue(size to polarity)
}

/** One filled region of an artwork, already displaced. */
internal class ValenceArtRegion(
    val contours: List<List<WobblePoint>>,
    val usesEvenOddFill: Boolean,
)

/** One artwork's wobbled art, in its own viewBox space. */
internal class ValenceArtwork(
    val viewBox: PebbleSvgModel.ViewBox,
    /** Every stroked path's leaky ink, merged. Always nonzero-filled. */
    val ink: List<List<WobblePoint>>,
    /** The artwork's filled regions, each with its own fill rule. */
    val regions: List<ValenceArtRegion>,
)

/**
 * The nine bundled valence artworks, wobbled once and kept — ports iOS
 * `ValenceArt.swift`.
 *
 * Each artwork holds two kinds of path, wobbled the two ways the renderer
 * already knows:
 *
 * - **Stroked** paths are centerlines carrying the width the PDF drew them
 *   with, inked through [WobbleRenderer.glyphInk] — the leaky-outline pass a
 *   carved glyph goes through. Their own widths rather than the uniform
 *   `PebbleStroke.OUTLINE_WIDTH` a real pebble uses: the box is ~190 units and
 *   the detail is fine, so the pebble weight merges it.
 * - **Filled** paths (one per artwork: the fossil's spiral) go through
 *   [WobbleRenderer.backdropArt], which displaces a region's contours instead
 *   of inking a line. Tracing a filled spiral as a centerline fills it in
 *   solid and the fossil reads as a blob.
 *
 * Pure JVM like the wobble module itself: the caller loads the markup (JVM code
 * can't reach Android resources) and hands it in, exactly as
 * `WobbleRenderer.backdropArt(assetName, raw)` is called. Deliberately
 * log-free for the same reason — a null return is the caller's cue to log and
 * render around it.
 */
internal object ValenceArt {
    // Nine artworks exist and the key is the asset name, so the map is bounded
    // by construction — no eviction policy needed, unlike the renderer's caches
    // which key on arbitrary markup.
    private val cache = HashMap<String, ValenceArtwork>()

    /**
     * Wobbled artwork for one asset. [assetName] is the cache key
     * (`"{size}-{polarity}"`); [raw] is the asset's markup. Null when the
     * markup is missing every parseable path — a setup bug, not a runtime
     * condition.
     */
    @Synchronized
    fun art(
        assetName: String,
        raw: String,
    ): ValenceArtwork? {
        cache[assetName]?.let { return it }
        val built = build(raw) ?: return null
        cache[assetName] = built
        return built
    }

    private fun build(raw: String): ValenceArtwork? {
        val viewBox = viewBox(raw) ?: return null
        val ink = mutableListOf<List<WobblePoint>>()
        val regions = mutableListOf<ValenceArtRegion>()

        for (element in paths(raw)) {
            if (element.isFilled) {
                // `backdropArt` takes asset markup rather than a path, so the
                // region is handed to it as the one-path asset it expects.
                val asset =
                    buildString {
                        append("<svg viewBox=\"")
                        append(viewBox.minX).append(' ').append(viewBox.minY).append(' ')
                        append(viewBox.width).append(' ').append(viewBox.height)
                        append("\"><path d=\"").append(element.pathData).append('"')
                        if (element.usesEvenOddFill) append(" fill-rule=\"evenodd\"")
                        append("/></svg>")
                    }
                val region = WobbleRenderer.backdropArt(asset) ?: continue
                regions += ValenceArtRegion(region.contours, region.usesEvenOddFill)
            } else {
                ink += WobbleRenderer.glyphInk(element.pathData, element.width) ?: continue
            }
        }

        if (ink.isEmpty() && regions.isEmpty()) return null
        return ValenceArtwork(viewBox = viewBox, ink = ink, regions = regions)
    }

    // ── Parsing ────────────────────────────────────────────────

    private class Element(
        val pathData: String,
        /** Meaningless when [isFilled]. */
        val width: Double,
        val isFilled: Boolean,
        val usesEvenOddFill: Boolean,
    )

    /**
     * The generated files are flat lists of self-closing `<path>` elements the
     * companion script writes, so a scan beats standing up an XML parser —
     * same call the renderer's own asset scanning makes.
     */
    private val pathPattern = Regex("<path\\s+([^>]*)/>")
    private val attributePattern = Regex("([\\w-]+)=\"([^\"]*)\"")
    private val viewBoxPattern = Regex("viewBox=\"([^\"]*)\"")

    private fun paths(svg: String): List<Element> =
        pathPattern
            .findAll(svg)
            .mapNotNull { match ->
                val attributes =
                    attributePattern
                        .findAll(match.groupValues[1])
                        .associate { it.groupValues[1] to it.groupValues[2] }
                val pathData = attributes["d"] ?: return@mapNotNull null
                Element(
                    pathData = pathData,
                    width = attributes["stroke-width"]?.toDoubleOrNull() ?: 0.0,
                    // The script writes `fill="none"` on every stroked path, so
                    // anything else — `currentColor` included — is a region.
                    isFilled = (attributes["fill"] ?: "none") != "none",
                    usesEvenOddFill = attributes["fill-rule"] == "evenodd",
                )
            }.toList()

    private fun viewBox(svg: String): PebbleSvgModel.ViewBox? {
        val value = viewBoxPattern.find(svg)?.groupValues?.get(1) ?: return null
        val parts = value.trim().split(Regex("[,\\s]+")).mapNotNull { it.toFloatOrNull() }
        if (parts.size != 4 || parts[2] <= 0f || parts[3] <= 0f) return null
        return PebbleSvgModel.ViewBox(parts[0], parts[1], parts[2], parts[3])
    }
}

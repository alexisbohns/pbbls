package app.pbbls.android.features.path.render

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

/**
 * Typed, render-agnostic view of a server-composed pebble SVG: the viewBox plus
 * the ordered `layer:*` groups, each carrying its opacity and its descendant
 * `<path>`s (path data + the flattened `translate`/`scale` transform inherited
 * from every enclosing `<g>`). The Android analog of iOS `PebbleSVGModel`.
 *
 * Deliberately pure — no Compose or `android.graphics` types — so the parse can
 * be unit-tested on the plain JVM (no Robolectric). [PebbleStaticRender] turns
 * this into Compose paths and traces every layer at [PebbleStroke.OUTLINE_WIDTH].
 *
 * [parsePebbleSvg] returns `null` when the markup has no viewBox or no traceable
 * layer, so the caller can fall back to the raw AndroidSVG renderer ([PebbleSvg]).
 */
internal data class PebbleSvgModel(
    val viewBox: ViewBox,
    val layers: List<Layer>,
) {
    /** Parsed `viewBox="minX minY width height"`. */
    data class ViewBox(
        val minX: Float,
        val minY: Float,
        val width: Float,
        val height: Float,
    )

    /** One `layer:*` group. [opacity] is the `<g opacity>` (1 when absent). */
    data class Layer(
        val opacity: Float,
        val paths: List<PathSpec>,
    )

    /**
     * One traceable `<path>`: its `d` data and the [transform] flattened from
     * every enclosing `<g transform>` (identity when none). Stroke width is
     * deliberately dropped — the renderer overrides it with the outline weight.
     */
    data class PathSpec(
        val d: String,
        val transform: Affine,
    )
}

/**
 * A 2-D affine transform limited to the `translate`/`scale` forms the engine
 * emits (matrix `[[a c e] [b d f] [0 0 1]]`). Maps a point `(x, y)` to
 * `(a·x + c·y + e, b·x + d·y + f)`.
 */
internal data class Affine(
    val a: Float,
    val b: Float,
    val c: Float,
    val d: Float,
    val e: Float,
    val f: Float,
) {
    /** `this ∘ other` — the transform that applies [other] first, then this. */
    fun concat(other: Affine): Affine =
        Affine(
            a = a * other.a + c * other.b,
            b = b * other.a + d * other.b,
            c = a * other.c + c * other.d,
            d = b * other.c + d * other.d,
            e = a * other.e + c * other.f + e,
            f = b * other.e + d * other.f + f,
        )

    companion object {
        val IDENTITY = Affine(1f, 0f, 0f, 1f, 0f, 0f)

        fun translate(
            tx: Float,
            ty: Float,
        ) = Affine(1f, 0f, 0f, 1f, tx, ty)

        fun scale(
            sx: Float,
            sy: Float,
        ) = Affine(sx, 0f, 0f, sy, 0f, 0f)
    }
}

/**
 * Parses [svg] into a [PebbleSvgModel], or `null` when it can't be traced
 * (malformed XML, missing viewBox, or no layer carries a stroked path).
 *
 * Uses SAX (available on both the JVM and Android) with a running stack of
 * enclosing-group transforms, mirroring the streaming walk in iOS
 * `PebbleSVGModel`. Paths that end up `fill="none"` **and** `stroke="none"` are
 * dropped: the engine emits such paths for shape-detail fills that `stripFills`
 * zeroed, and AndroidSVG skips them — tracing them would draw spurious strokes.
 */
internal fun parsePebbleSvg(svg: String): PebbleSvgModel? {
    val handler = PebbleSvgHandler()
    return try {
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = false }
        // Defensive: the input is our own engine output, but keep the parser
        // from ever reaching for a DTD / external entity.
        runCatching { factory.setFeature(LOAD_EXTERNAL_DTD_FEATURE, false) }
        factory.newSAXParser().parse(InputSource(StringReader(svg)), handler)
        val viewBox = handler.viewBox ?: return null
        val layers = handler.layers.mapNotNull { it.toLayerOrNull() }
        if (layers.isEmpty()) null else PebbleSvgModel(viewBox, layers)
    } catch (e: Exception) {
        // Malformed markup is a runtime data condition, not a crash — the caller
        // falls back to AndroidSVG. (Broad catch: SAX can throw beyond
        // SAXException, e.g. IOException from the reader.)
        null
    }
}

// ── SAX handler ─────────────────────────────────────────────

private class MutableLayer(
    val opacity: Float,
) {
    val paths = mutableListOf<PebbleSvgModel.PathSpec>()

    fun toLayerOrNull(): PebbleSvgModel.Layer? = if (paths.isEmpty()) null else PebbleSvgModel.Layer(opacity, paths.toList())
}

/** One `<g>` frame: the flattened transform in effect, and the layer it feeds. */
private class GroupFrame(
    val transform: Affine,
    val layer: MutableLayer?,
)

private class PebbleSvgHandler : DefaultHandler() {
    var viewBox: PebbleSvgModel.ViewBox? = null
    val layers = mutableListOf<MutableLayer>()
    private val stack = ArrayDeque<GroupFrame>()

    override fun startElement(
        uri: String?,
        localName: String?,
        qName: String?,
        attributes: Attributes,
    ) {
        when (qName) {
            "svg" -> viewBox = parseViewBox(attributes.getValue("viewBox"))
            "g" -> {
                val parent = stack.lastOrNull()
                val parentTransform = parent?.transform ?: Affine.IDENTITY
                val own = attributes.getValue("transform")?.let(::parseTransform) ?: Affine.IDENTITY
                val transform = parentTransform.concat(own)
                val id = attributes.getValue("id")
                val layer =
                    if (id != null && id.startsWith("layer:")) {
                        val opacity = attributes.getValue("opacity")?.toFloatOrNull() ?: 1f
                        MutableLayer(opacity).also { layers.add(it) }
                    } else {
                        // A non-layer group (e.g. the glyph's inner normalization
                        // <g>) feeds whatever layer its parent belongs to.
                        parent?.layer
                    }
                stack.addLast(GroupFrame(transform, layer))
            }
            "path" -> {
                val frame = stack.lastOrNull() ?: return
                val layer = frame.layer ?: return
                val d = attributes.getValue("d") ?: return
                // SVG spec defaults: fill black, stroke none. A path left with
                // neither (a stripped shape-detail fill) is invisible — skip it.
                val fill = attributes.getValue("fill") ?: "black"
                val stroke = attributes.getValue("stroke") ?: "none"
                if (fill == "none" && stroke == "none") return
                layer.paths.add(PebbleSvgModel.PathSpec(d, frame.transform))
            }
        }
    }

    override fun endElement(
        uri: String?,
        localName: String?,
        qName: String?,
    ) {
        if (qName == "g") stack.removeLastOrNull()
    }
}

// ── Attribute parsing ───────────────────────────────────────

private const val LOAD_EXTERNAL_DTD_FEATURE = "http://apache.org/xml/features/nonvalidating/load-external-dtd"
private val ARG_SEPARATOR = Regex("[,\\s]+")
private val TRANSFORM_OP = Regex("(translate|scale)\\s*\\(([^)]*)\\)")

private fun parseViewBox(raw: String?): PebbleSvgModel.ViewBox? {
    if (raw == null) return null
    val parts = raw.trim().split(ARG_SEPARATOR).mapNotNull { it.toFloatOrNull() }
    if (parts.size != 4) return null
    return PebbleSvgModel.ViewBox(parts[0], parts[1], parts[2], parts[3])
}

/**
 * Parses the limited transform forms the engine emits —
 * `translate(x, y) scale(s)`, `translate(x, y)`, `scale(sx, sy)` (commas
 * optional) — composing left-to-right so the leftmost op is outermost (applied
 * last to a point), matching SVG semantics. Unrecognized forms collapse to
 * identity.
 */
internal fun parseTransform(value: String): Affine {
    var result = Affine.IDENTITY
    for (match in TRANSFORM_OP.findAll(value)) {
        val name = match.groupValues[1]
        val args = match.groupValues[2].split(ARG_SEPARATOR).mapNotNull { it.trim().toFloatOrNull() }
        val op =
            when (name) {
                "translate" -> Affine.translate(args.getOrElse(0) { 0f }, args.getOrElse(1) { 0f })
                "scale" -> {
                    val sx = args.getOrElse(0) { 1f }
                    Affine.scale(sx, args.getOrElse(1) { sx })
                }
                else -> Affine.IDENTITY
            }
        result = result.concat(op)
    }
    return result
}

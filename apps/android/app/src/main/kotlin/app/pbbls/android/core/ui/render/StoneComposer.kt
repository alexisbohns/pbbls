package app.pbbls.android.core.ui.render

import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import kotlin.math.min

/** Top-left and side of the glyph's square slot in the stone canvas. */
internal data class GlyphSlot(
    val x: Float,
    val y: Float,
    val size: Float,
)

/**
 * Composes a stone on device, as the server engine did before spec D6 deleted
 * it (`functions/_shared/engine/compose.ts` and `layout.ts`, readable at git
 * `13b5ae3b^`): the shape seed's stroked paths as `layer:shape`, then the
 * glyph's strokes as `layer:glyph`, its view box fitted into the slot.
 *
 * One deliberate departure (A-10): no bounding-box normalization of the
 * strokes. The glyph's own view box maps into the slot, which is how every
 * other glyph surface on Android draws a glyph.
 */
internal object StoneComposer {
    private val slots: Map<Pair<ValenceSizeGroup, ValencePolarity>, GlyphSlot> =
        mapOf(
            (ValenceSizeGroup.SMALL to ValencePolarity.HIGHLIGHT) to GlyphSlot(37.5f, 30f, 140f),
            (ValenceSizeGroup.SMALL to ValencePolarity.NEUTRAL) to GlyphSlot(25f, 30f, 140f),
            (ValenceSizeGroup.SMALL to ValencePolarity.LOWLIGHT) to GlyphSlot(30f, 30f, 140f),
            (ValenceSizeGroup.MEDIUM to ValencePolarity.HIGHLIGHT) to GlyphSlot(26f, 84f, 150f),
            (ValenceSizeGroup.MEDIUM to ValencePolarity.NEUTRAL) to GlyphSlot(26f, 55f, 150f),
            (ValenceSizeGroup.MEDIUM to ValencePolarity.LOWLIGHT) to GlyphSlot(39f, 26f, 150f),
            (ValenceSizeGroup.LARGE to ValencePolarity.HIGHLIGHT) to GlyphSlot(50f, 75f, 160f),
            (ValenceSizeGroup.LARGE to ValencePolarity.NEUTRAL) to GlyphSlot(26f, 88f, 160f),
            (ValenceSizeGroup.LARGE to ValencePolarity.LOWLIGHT) to GlyphSlot(26f, 75f, 160f),
        )

    private val anyFill = Regex("fill=\"[^\"]*\"")
    private val svgOpen = Regex("<svg[^>]*>")

    fun glyphSlot(
        size: ValenceSizeGroup,
        polarity: ValencePolarity,
    ): GlyphSlot = slots.getValue(size to polarity)

    /**
     * The seed as a single `layer:shape`. Every fill becomes `none`, as the
     * engine's `stripFills` did, so the filled detail paths (no stroke) are
     * dropped by [parsePebbleSvg] and only the stroked outline is traced.
     */
    fun shapeModel(seedSvg: String): PebbleSvgModel? =
        parsePebbleSvg(
            seedSvg
                .replace(anyFill, "fill=\"none\"")
                .replaceFirst(svgOpen, "\$0<g id=\"layer:shape\">")
                .replace("</svg>", "</g></svg>"),
        )

    fun compose(
        shape: PebbleSvgModel,
        size: ValenceSizeGroup,
        polarity: ValencePolarity,
        glyph: PebbleGlyph?,
    ): PebbleSvgModel {
        val layer = glyph?.let { glyphLayer(it, glyphSlot(size, polarity)) } ?: return shape
        return shape.copy(layers = shape.layers + layer)
    }

    /**
     * Keyed on the glyph's content as well as its id: a collection glyph
     * edited in place keeps its id, and the wobble cache must not serve the
     * old carving.
     */
    fun cacheKey(
        size: ValenceSizeGroup,
        polarity: ValencePolarity,
        glyph: PebbleGlyph?,
    ): String = "stone:${size.key}:${polarity.key}:${glyph?.let { "${it.id}:${(it.viewBox to it.strokes).hashCode()}" } ?: "-"}"

    private fun glyphLayer(
        glyph: PebbleGlyph,
        slot: GlyphSlot,
    ): PebbleSvgModel.Layer? {
        val box = parseViewBox(glyph.viewBox) ?: return null
        if (box.width <= 0f || box.height <= 0f) return null
        val scale = min(slot.size / box.width, slot.size / box.height)
        val transform =
            Affine
                .translate(slot.x, slot.y)
                .concat(Affine.scale(scale, scale))
                .concat(Affine.translate(-box.minX, -box.minY))
        return PebbleSvgModel.Layer(
            kind = PebbleSvgModel.Layer.Kind.GLYPH,
            transform = transform,
            opacity = 1f,
            paths = glyph.strokes.map { PebbleSvgModel.PathSpec(d = it.d, transform = Affine.IDENTITY) },
        )
    }
}

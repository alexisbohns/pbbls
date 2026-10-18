package app.pbbls.android.core.ui.render.stone

import androidx.compose.ui.graphics.vector.PathParser
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.ui.render.Affine
import app.pbbls.android.core.ui.render.PebbleOutlineGeometry
import app.pbbls.android.core.ui.render.StoneComposer
import app.pbbls.android.core.ui.render.wobble.WobblePathFlattener
import kotlin.math.max
import kotlin.math.min

/**
 * Where each part of a lit stone sits — the web's `stone-geometry.ts`
 * transcribed (#1122). Three spaces meet in one stone:
 *
 * - outline units: the backdrop silhouette's own viewBox (337 × 270 for
 *   small). The body, its clip and the rings are drawn in it.
 * - canvas units: the engine canvas (250 × 200 for small) fitted and centred
 *   into the outline box, through [canvasTransform]. The material constants
 *   lifted from the web mock (facets, specks, sheen, glint) are read verbatim
 *   inside it.
 * - seed units: the engine canvas again, but placed with iOS's carving inset
 *   (`pebbleScale × 1.22`), through [carveTransform]. Veins, fossil and
 *   glyph live here.
 *
 * Pixel constants are the mock's, measured on its phone stones; they convert
 * to outline units through [unitsPerPx]. Transforms are [Affine] values so
 * the math stays testable on the JVM; the painter converts them to a
 * `Matrix` at draw time.
 */
internal object StoneGeometry {
    data class Box(
        val width: Float,
        val height: Float,
    )

    data class GlyphZone(
        val x: Float,
        val y: Float,
        val size: Float,
    )

    /** Area centroid and bounding box of a silhouette's outer contour, in outline units. */
    data class Frame(
        val cx: Float,
        val cy: Float,
        val width: Float,
        val height: Float,
    )

    /** One transform per ring, innermost first, then the halo's. */
    data class RingGeometry(
        val rings: List<Affine>,
        val halo: Affine,
    )

    /** The sprite's blurs in canvas units, as the mock declared them. */
    object Blur {
        const val NEAR = 5f
        const val FAR = 15f
        const val AO = 7f
        const val SOFT = 12f
        const val GLOW = 4f
    }

    /** Stone width on a phone; the height follows the silhouette's aspect ratio. */
    val STONE_WIDTH_DP: Map<ValenceSizeGroup, Float> =
        mapOf(
            ValenceSizeGroup.SMALL to 34f,
            ValenceSizeGroup.MEDIUM to 44f,
            ValenceSizeGroup.LARGE to 54f,
        )

    /** iOS `StoneLook.carvingScale`: the carving's inset relative to the engine's. */
    const val CARVE_SCALE = 1.22f

    /** The glyph's carved line, whatever the stone's size. */
    const val CARVE_STROKE_PX = 1.25f

    /** The engine canvas for a size (all three polarities share it). */
    val SEED_CANVAS: Map<ValenceSizeGroup, Box> =
        mapOf(
            ValenceSizeGroup.SMALL to Box(250f, 200f),
            ValenceSizeGroup.MEDIUM to Box(260f, 260f),
            ValenceSizeGroup.LARGE to Box(260f, 310f),
        )

    /** The two blurred copies under the stone, pushed away from the light. */
    val SHADOW_FAR_PX: Pair<Float, Float> = 3.86f to 6.95f
    val SHADOW_NEAR_PX: Pair<Float, Float> = 0.93f to 2.01f

    /** The glyph's lit lip, drawn under the ink and pushed down-right. */
    val LIP_PX: Pair<Float, Float> = 0.32f to 0.54f

    val RING_COUNT: Map<ValenceSizeGroup, Int> =
        mapOf(
            ValenceSizeGroup.SMALL to 1,
            ValenceSizeGroup.MEDIUM to 2,
            ValenceSizeGroup.LARGE to 3,
        )

    private val RING_STEP_PX: Map<ValenceSizeGroup, Float> =
        mapOf(
            ValenceSizeGroup.SMALL to 4.8f,
            ValenceSizeGroup.MEDIUM to 4.4f,
            ValenceSizeGroup.LARGE to 4.2f,
        )

    private const val HALO_STEP = 0.78

    /** The web flattens the silhouette at this step before taking its frame; match it or the centroids drift. */
    private const val FRAME_FLATTEN_STEP = 2.0

    private const val DEFAULT_GLYPH_SIDE = 200f

    private val ringCache = HashMap<Pair<ValenceSizeGroup, ValencePolarity>, RingGeometry>()

    fun outlineBox(size: ValenceSizeGroup): Box = PebbleOutlineGeometry.outlineDimensions(size).let { Box(it.width, it.height) }

    /** Outline units per on-screen px on a phone. */
    fun unitsPerPx(size: ValenceSizeGroup): Float = outlineBox(size).width / STONE_WIDTH_DP.getValue(size)

    /** Canvas units → outline units: the mock's stone, fitted to the silhouette box. */
    fun canvasScale(size: ValenceSizeGroup): Float {
        val outline = outlineBox(size)
        val seed = SEED_CANVAS.getValue(size)
        return min(outline.width / seed.width, outline.height / seed.height)
    }

    @Suppress("UNUSED_PARAMETER")
    fun canvasTransform(
        size: ValenceSizeGroup,
        polarity: ValencePolarity,
    ): Affine = centred(size, canvasScale(size))

    /** Seed units → outline units, with iOS's carving inset (`pebbleScale × 1.22`). */
    @Suppress("UNUSED_PARAMETER")
    fun carveTransform(
        size: ValenceSizeGroup,
        polarity: ValencePolarity,
    ): Affine = centred(size, canvasScale(size) * PebbleOutlineGeometry.pebbleScale(size) * CARVE_SCALE)

    /** The glyph's zone in seed units, top-left origin — the composer's table. */
    fun glyphZone(
        size: ValenceSizeGroup,
        polarity: ValencePolarity,
    ): GlyphZone = StoneComposer.glyphSlot(size, polarity).let { GlyphZone(it.x, it.y, it.size) }

    /** The glyph in its zone, in seed units. [viewBoxSide] is the glyph's own square side. */
    fun glyphTransform(
        size: ValenceSizeGroup,
        polarity: ValencePolarity,
        viewBoxSide: Float,
    ): Affine {
        val zone = glyphZone(size, polarity)
        val scale = zone.size / viewBoxSide
        return Affine(scale, 0f, 0f, scale, zone.x, zone.y)
    }

    /**
     * The rings are the silhouette scaled about its centroid, each a step
     * further out; the halo sits [HALO_STEP] of a step past the last ring.
     * Cached per shape: the frame flattens the whole outline.
     */
    fun ringGeometry(
        size: ValenceSizeGroup,
        polarity: ValencePolarity,
        outlinePathData: String,
    ): RingGeometry =
        synchronized(ringCache) {
            ringCache.getOrPut(size to polarity) { buildRings(size, outlinePathData) }
        }

    private fun buildRings(
        size: ValenceSizeGroup,
        outlinePathData: String,
    ): RingGeometry {
        val frame = silhouetteFrame(outlinePathData)
        val step = RING_STEP_PX.getValue(size).toDouble() * unitsPerPx(size)
        val cx = frame.cx.toDouble()
        val cy = frame.cy.toDouble()

        fun at(k: Double): Affine {
            val sx = 1 + 2 * k * step / frame.width
            val sy = 1 + 2 * k * step / frame.height
            return Affine(sx.toFloat(), 0f, 0f, sy.toFloat(), (cx * (1 - sx)).toFloat(), (cy * (1 - sy)).toFloat())
        }
        val count = RING_COUNT.getValue(size)
        return RingGeometry(
            rings = List(count) { at((it + 1).toDouble()) },
            halo = at(count + HALO_STEP),
        )
    }

    /**
     * Area centroid (shoelace) and bounding box of the path's first contour.
     * The first contour is the outer one; large-lowlight's second is its hole.
     */
    fun silhouetteFrame(outlinePathData: String): Frame {
        val nodes =
            try {
                PathParser().parsePathString(outlinePathData).toNodes()
            } catch (e: IllegalArgumentException) {
                emptyList()
            }
        val points =
            WobblePathFlattener
                .flatten(nodes, FRAME_FLATTEN_STEP)
                .firstOrNull()
                ?.points
                .orEmpty()
        if (points.isEmpty()) return Frame(0f, 0f, 0f, 0f)
        var area = 0.0
        var cx = 0.0
        var cy = 0.0
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        points.forEachIndexed { i, p ->
            val q = points[(i + 1) % points.size]
            val cross = p.x * q.y - q.x * p.y
            area += cross
            cx += (p.x + q.x) * cross
            cy += (p.y + q.y) * cross
            minX = min(minX, p.x)
            minY = min(minY, p.y)
            maxX = max(maxX, p.x)
            maxY = max(maxY, p.y)
        }
        area /= 2
        return Frame(
            cx = (cx / (6 * area)).toFloat(),
            cy = (cy / (6 * area)).toFloat(),
            width = (maxX - minX).toFloat(),
            height = (maxY - minY).toFloat(),
        )
    }

    /** A glyph's square side from its `viewBox` ("0 0 200 200"); 200 if unreadable. */
    fun glyphSide(viewBox: String): Float {
        val side =
            viewBox
                .trim()
                .split(Regex("[\\s,]+"))
                .getOrNull(2)
                ?.toFloatOrNull()
        return if (side != null && side.isFinite() && side > 0f) side else DEFAULT_GLYPH_SIDE
    }

    private fun centred(
        size: ValenceSizeGroup,
        scale: Float,
    ): Affine {
        val outline = outlineBox(size)
        val seed = SEED_CANVAS.getValue(size)
        val tx = (outline.width - seed.width * scale) / 2
        val ty = (outline.height - seed.height * scale) / 2
        return Affine(scale, 0f, 0f, scale, tx, ty)
    }
}

private val pathDataAttribute = Regex("<path\\b[^>]*?\\sd=\"([^\"]*)\"")

/** The `d` of an outline asset's single `<path>`; empty when the asset has none. */
internal fun outlinePathData(raw: String): String = pathDataAttribute.find(raw)?.groupValues?.get(1) ?: ""

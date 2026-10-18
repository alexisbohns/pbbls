package app.pbbls.android.core.ui.render.stone

import android.content.res.Resources
import android.util.Log
import androidx.compose.ui.graphics.vector.PathParser
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.ui.render.PebbleSvgModel
import app.pbbls.android.core.ui.render.StoneShapeAssets
import app.pbbls.android.core.ui.render.parseViewBox
import app.pbbls.android.core.ui.render.wobble.WobblePathFlattener
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import java.util.concurrent.ConcurrentHashMap
import javax.xml.parsers.SAXParserFactory
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** The seed's filled path, with its `fill-rule` so the lit stone fills its holes the way the engine did. */
internal data class StoneFossil(
    val d: String,
    val evenOdd: Boolean,
)

/**
 * One shape seed's carvings in the seed's own canvas units ([width] × [height]
 * from its viewBox). The outline is parsed but not kept: the lit stone's edge
 * is the backdrop silhouette (spec D3).
 */
internal data class StoneCarving(
    val width: Float,
    val height: Float,
    val veins: List<String>,
    val fossil: StoneFossil?,
)

/**
 * Splits an engine shape seed (`res/raw/shape_{size}_{polarity}.svg`) into
 * veins and fossil — the Android port of iOS `StoneShapeParser` and the web's
 * `shape-seed.ts`, reading the seeds the same way they do (#1122). The engine
 * emits no ids: among the stroked `<path>`s the one with the largest extent is
 * the outline (`large-neutral` lists it third, so document order is not
 * enough), every other stroked path is a vein in document order, and the
 * first filled path is the fossil.
 */
internal object StoneCarvings {
    /** Positions in the seed's `<path>` document order; [fossilIndex] is null when the seed has no filled path. */
    data class Classification(
        val outlineIndex: Int,
        val veinIndices: List<Int>,
        val fossilIndex: Int?,
    )

    /** The web flattens at this step for its frames; the extent only needs to rank paths, so it shares it. */
    private const val EXTENT_FLATTEN_STEP = 2.0

    private val cache = ConcurrentHashMap<Int, StoneCarving>()

    /** Pure: one seed's SVG text. Null when it has no viewBox or no stroked path. */
    fun parse(seedSvg: String): StoneCarving? {
        val seed = read(seedSvg) ?: return null
        val classification = classify(seed) ?: return null
        return StoneCarving(
            width = seed.width,
            height = seed.height,
            veins = classification.veinIndices.map { seed.paths[it].d },
            fossil =
                classification.fossilIndex?.let {
                    val path = seed.paths[it]
                    StoneFossil(path.d, evenOdd = path.fillRule == "evenodd")
                },
        )
    }

    /** Which path plays which part, for the tests that pin the outline rule. */
    fun classify(seedSvg: String): Classification? = read(seedSvg)?.let(::classify)

    /** Cached per resource id, like `StoneShapes.parsedShapes`; a seed that does not parse is logged and not cached. */
    fun of(
        resources: Resources,
        size: ValenceSizeGroup,
        polarity: ValencePolarity,
    ): StoneCarving? {
        val resId = StoneShapeAssets.resId(size, polarity)
        return cache[resId]
            ?: resources
                .openRawResource(resId)
                .bufferedReader()
                .use { it.readText() }
                .let(::parse)
                ?.also { cache[resId] = it }
                .also { if (it == null) Log.e("stone", "shape seed $resId has no carvings") }
    }

    private fun classify(seed: Seed): Classification? {
        val stroked = seed.paths.indices.filter { seed.paths[it].isStroked }
        if (stroked.isEmpty()) return null
        // `maxBy` keeps the first maximum: a tie falls to the earlier path (none occurs in the nine seeds).
        val outline = stroked.maxBy { extent(seed.paths[it].d) }
        return Classification(
            outlineIndex = outline,
            veinIndices = stroked.filter { it != outline },
            fossilIndex = seed.paths.indices.firstOrNull { seed.paths[it].isFilled },
        )
    }

    /** Bounding-box diagonal of the flattened path, over all its contours. */
    private fun extent(d: String): Double {
        val nodes =
            try {
                PathParser().parsePathString(d).toNodes()
            } catch (e: IllegalArgumentException) {
                return 0.0
            }
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        WobblePathFlattener.flatten(nodes, EXTENT_FLATTEN_STEP).forEach { contour ->
            contour.points.forEach { p ->
                minX = min(minX, p.x)
                minY = min(minY, p.y)
                maxX = max(maxX, p.x)
                maxY = max(maxY, p.y)
            }
        }
        return if (minX > maxX) 0.0 else hypot(maxX - minX, maxY - minY)
    }

    // ── Reading the seed ────────────────────────────────────────

    private class SeedPath(
        val d: String,
        val fill: String?,
        val stroke: String?,
        val fillRule: String?,
    ) {
        /** An explicit fill wins over a stroke, as on iOS and the web; the engine never writes both. */
        val isFilled: Boolean get() = fill != null && fill != "none"
        val isStroked: Boolean get() = !isFilled && stroke != null && stroke != "none"
    }

    private class Seed(
        val width: Float,
        val height: Float,
        val paths: List<SeedPath>,
    )

    private fun read(seedSvg: String): Seed? {
        val handler = SeedHandler()
        return try {
            val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = false }
            runCatching { factory.setFeature(LOAD_EXTERNAL_DTD_FEATURE, false) }
            factory.newSAXParser().parse(InputSource(StringReader(seedSvg)), handler)
            val viewBox = handler.viewBox ?: return null
            Seed(viewBox.width, viewBox.height, handler.paths)
        } catch (e: Exception) {
            // Malformed markup is a data condition, not a crash: the stone draws without carvings.
            // (Broad catch: SAX can throw beyond SAXException, e.g. IOException from the reader.)
            null
        }
    }

    private class SeedHandler : DefaultHandler() {
        var viewBox: PebbleSvgModel.ViewBox? = null
        val paths = mutableListOf<SeedPath>()

        override fun startElement(
            uri: String?,
            localName: String?,
            qName: String?,
            attributes: Attributes,
        ) {
            when (qName) {
                "svg" -> viewBox = parseViewBox(attributes.getValue("viewBox"))
                "path" -> {
                    val d = attributes.getValue("d") ?: return
                    paths.add(
                        SeedPath(
                            d = d,
                            fill = attributes.getValue("fill"),
                            stroke = attributes.getValue("stroke"),
                            fillRule = attributes.getValue("fill-rule"),
                        ),
                    )
                }
            }
        }
    }

    private const val LOAD_EXTERNAL_DTD_FEATURE = "http://apache.org/xml/features/nonvalidating/load-external-dtd"
}

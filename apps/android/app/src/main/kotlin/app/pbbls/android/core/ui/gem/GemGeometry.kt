package app.pbbls.android.core.ui.gem

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

/** A point of the cut, in the gem's units (the round stone's girdle is 80 from the centre; y down). */
data class GemPoint(
    val x: Double,
    val y: Double,
)

enum class FacetKind { TABLE, STAR, KITE, GIRDLE }

/**
 * One facet of the cut: [theta] is its direction in sevenths of a turn from the top, clockwise;
 * [tone] its light, 0 (lit) to 1 (in shade), fixed by the cut and never by data.
 */
data class GemFacet(
    val id: String,
    val kind: FacetKind,
    val theta: Double?,
    val points: List<GemPoint>,
    val tone: Double,
)

/** An edge two facets share, for the seams. */
data class GemEdge(
    val a: GemPoint,
    val b: GemPoint,
    val facets: Pair<String, String>,
)

/**
 * The emotions gem's fixed cut (#1207, D10): the faithful brilliant of the round-5 board, a port of
 * `gem-method.js`'s `geometry` (the oracle the server's `gem_allocate` is proven against) with the
 * board's lighting. Display only: which family owns a facet is the server's `gem.facets`; this never
 * decides it. The table, 7 stars, 7 kites and 14 girdle facets; the 28 around the table are the
 * allocation's ids.
 */
object GemGeometry {
    const val N = 7
    private const val STEP = 2 * PI / N
    private const val HALF = STEP / 2

    // The oracle's SHAPE: table corners at T, star points at RS, kite tips at the reach profile.
    private const val T = 35.0
    private const val RS = 53.0
    private const val R0 = 80.0
    private val REACH = listOf(88.0, 84.0, 79.0, 76.0, 76.0, 79.0, 84.0)
    private const val ARC_STEPS = 24

    // The board's light: the table raised by a 34.5° crown, one light 38° up from the upper left.
    private const val CROWN_DEG = 34.5
    private const val LIGHT_ELEVATION_DEG = 38.0
    private const val LIGHT_AZIMUTH_DEG = -135.0

    val facets: List<GemFacet> by lazy { cut(REACH).let { raw -> raw.map { it.copy(tone = tone(it)) } } }

    val byId: Map<String, GemFacet> by lazy { facets.associateBy { it.id } }

    /** The 28 ids the allocation colours, in the oracle's order. */
    val allocated: List<String> by lazy { facets.filter { it.kind != FacetKind.TABLE }.map { it.id } }

    val edges: List<GemEdge> by lazy { sharedEdges(facets) }

    /** The cut's bounding box: (left, top, right, bottom). */
    val bounds: List<Double> by lazy {
        val all = facets.flatMap { it.points }
        listOf(all.minOf { it.x }, all.minOf { it.y }, all.maxOf { it.x }, all.maxOf { it.y })
    }

    private val rim: List<Pair<Double, Double>> by lazy {
        facets.filter { it.kind == FacetKind.GIRDLE }.flatMap { it.points }.map { atan2(it.y, it.x) to hypot(it.x, it.y) }
    }

    /** The round stone's facet areas by kind: the allocation's nominal areas (`gem_allocate`'s constants). */
    fun nominalAreas(): Map<FacetKind, Double> =
        cut(List(N) { R0 }).groupBy { it.kind }.mapValues { (_, fs) ->
            shoelace(fs.first().points)
        }

    /** The rim's radius toward [angle] (radians, 0 at three o'clock, clockwise), for placing names outside. */
    fun rimRadius(angle: Double): Double = rim.minBy { (a, _) -> abs(atan2(sin(a - angle), cos(a - angle))) }.second

    /** The area-weighted circular mean of [ids]' directions, in radians: where a family's run sits. */
    fun runAngle(ids: List<String>): Double {
        var x = 0.0
        var y = 0.0
        val areas = nominalAreas()
        ids.mapNotNull(byId::get).forEach { f ->
            val a = -PI / 2 + (f.theta ?: 0.0) * STEP
            val w = areas.getValue(f.kind)
            x += w * cos(a)
            y += w * sin(a)
        }
        return if (hypot(x, y) < 1e-6) -PI / 2 else atan2(y, x)
    }

    private fun ang(p: Int) = -PI / 2 + p * STEP

    private fun at(
        a: Double,
        r: Double,
    ) = GemPoint(r * cos(a), r * sin(a))

    private fun cut(reach: List<Double>): List<GemFacet> {
        fun r(p: Int) = reach[((p % N) + N) % N]
        val tc = List(N) { at(ang(it), T) }
        val sk = List(N) { at(ang(it) + HALF, RS) }
        val gt = List(N) { at(ang(it), r(it)) }
        val re = List(N) { (r(it) + r(it + 1)) / 2 }
        val ge = List(N) { at(ang(it) + HALF, re[it]) }

        // The rim between two girdle points: polar steps, the radius eased.
        fun arc(
            a0: Double,
            r0: Double,
            a1: Double,
            r1: Double,
        ): List<GemPoint> =
            (1..ARC_STEPS).map { s ->
                val t = s.toDouble() / ARC_STEPS
                val e = (1 - cos(t * PI)) / 2
                at(a0 + (a1 - a0) * t, r0 + (r1 - r0) * e)
            }

        val out = mutableListOf<GemFacet>()
        out += GemFacet("table", FacetKind.TABLE, null, tc, 0.0)
        for (p in 0 until N) out += GemFacet("star_$p", FacetKind.STAR, p + 0.5, listOf(tc[p], tc[(p + 1) % N], sk[p]), 0.0)
        for (p in 0 until N) out += GemFacet("kite_$p", FacetKind.KITE, p.toDouble(), listOf(tc[p], sk[p], gt[p], sk[(p + N - 1) % N]), 0.0)
        for (p in 0 until N) {
            val h = (p + N - 1) % N
            out +=
                GemFacet(
                    "girdle_${2 * p}",
                    FacetKind.GIRDLE,
                    p - 0.25,
                    listOf(gt[p], sk[h], ge[h]) + arc(ang(p) - HALF, re[h], ang(p), r(p)).dropLast(1),
                    0.0,
                )
            out +=
                GemFacet(
                    "girdle_${2 * p + 1}",
                    FacetKind.GIRDLE,
                    p + 0.25,
                    listOf(gt[p]) + arc(ang(p), r(p), ang(p) + HALF, re[p]) + sk[p],
                    0.0,
                )
        }
        return out
    }

    private fun shoelace(pts: List<GemPoint>): Double {
        var s = 0.0
        pts.indices.forEach { i ->
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            s += a.x * b.y - b.x * a.y
        }
        return abs(s) / 2
    }

    private val lightDir: DoubleArray by lazy {
        val e = LIGHT_ELEVATION_DEG * PI / 180
        val az = LIGHT_AZIMUTH_DEG * PI / 180
        doubleArrayOf(cos(e) * cos(az), cos(e) * sin(az), sin(e))
    }

    private val tableHeight = (R0 - T) * tan(CROWN_DEG * PI / 180)
    private val starHeight = tableHeight * (1 - (RS * cos(STEP / 2) - T) / (R0 - T))

    private fun lift(q: GemPoint): DoubleArray {
        val r = hypot(q.x, q.y)
        val z =
            when {
                abs(r - T) < 0.5 -> tableHeight
                abs(r - RS) < 0.5 -> starHeight
                else -> 0.0
            }
        return doubleArrayOf(q.x, q.y, z)
    }

    private fun newell(pts: List<DoubleArray>): DoubleArray {
        var nx = 0.0
        var ny = 0.0
        var nz = 0.0
        pts.indices.forEach { i ->
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            nx += (a[1] - b[1]) * (a[2] + b[2])
            ny += (a[2] - b[2]) * (a[0] + b[0])
            nz += (a[0] - b[0]) * (a[1] + b[1])
        }
        val l = Math.sqrt(nx * nx + ny * ny + nz * nz).takeIf { it > 0 } ?: 1.0
        val n = doubleArrayOf(nx / l, ny / l, nz / l)
        return if (n[2] < 0) DoubleArray(3) { -n[it] } else n
    }

    /** The board's flat tone: the light on the lifted facet, then a per-kind range (kites never full shade). */
    private fun tone(f: GemFacet): Double {
        if (f.kind == FacetKind.TABLE) return 0.0
        val n = newell(f.points.map(::lift))
        val light = n[0] * lightDir[0] + n[1] * lightDir[1] + n[2] * lightDir[2]
        val s = ((0.97 - light) / 0.78).coerceIn(0.0, 1.0)
        return when (f.kind) {
            FacetKind.KITE -> 0.12 + s * 0.72
            FacetKind.STAR -> s * 0.8
            else -> s
        }
    }

    private fun sharedEdges(facets: List<GemFacet>): List<GemEdge> {
        fun key(q: GemPoint) = "${Math.round(q.x * 100)},${Math.round(q.y * 100)}"
        val found = LinkedHashMap<String, Triple<GemPoint, GemPoint, MutableList<String>>>()
        facets.forEach { f ->
            f.points.forEachIndexed { i, a ->
                val b = f.points[(i + 1) % f.points.size]
                val ka = key(a)
                val kb = key(b)
                val k = if (ka < kb) "$ka|$kb" else "$kb|$ka"
                found.getOrPut(k) { Triple(a, b, mutableListOf()) }.third += f.id
            }
        }
        return found.values.filter { it.third.size == 2 }.map { GemEdge(it.first, it.second, it.third[0] to it.third[1]) }
    }
}

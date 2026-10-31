package app.pbbls.android.core.designsystem.stone

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How a stone is drawn. Units are the outline's own (dp on screen, points on iOS).
 *
 * @property corner base corner radius; the four corners are 1.15, 0.95, 0.8 and 1.05 of it.
 * @property bow scale of the sides' outward bulge.
 * @property gutter the open gap a cut leaves between two pieces.
 * @property fillet the radius every fresh cut is rounded by.
 * @property lean the cuts' horizontal run per unit of height. `null` keeps iOS's
 *   drawn lean (0.11 ± 0.03, the draw after the rock's nine), so the default
 *   call reproduces the shipping quick actions exactly.
 */
data class StoneOptions(
    val corner: Float = 34f,
    val bow: Float = 1f,
    val gutter: Float = 8f,
    val fillet: Float = 20f,
    val lean: Float? = null,
)

/**
 * The pebble shapes behind Home and You: one rock, or one rock cut into n
 * pieces. A port of iOS `ShortcutStones` (`apps/ios/Pebbles/Features/Home/Shortcuts/ShortcutStones.swift`),
 * arithmetic in the same order and in `Double` as on iOS, generalised the way
 * the mock kit's `stones.js` generalises it (n pieces and [StoneOptions]).
 * Every draw comes from [StoneRandom] on a constant seed, so a seed always
 * gives the same stone on every surface (spec 2026-10-31 Android Home and You, D5).
 *
 * Outlines are lists of points in the box they were asked for; [path] turns
 * one into a smooth closed [Path]. Results are memoised per size (to a
 * quarter unit), seed and options, and the memo is cleared past 24 entries,
 * as on iOS.
 */
object StoneGeometry {
    /** The shipping quick actions row's seed (iOS). */
    const val QUICK_ACTIONS_SEED = "camera|fixed|rock|q11"

    /** Appended to each bento shortcut's raw value to seed its corner (iOS). */
    const val BENTO_SEED_SUFFIX = "|fixed|q12split"

    private const val BENTO_GUTTER = 8.0
    private const val MEMO_CAP = 24
    private const val RAYS = 180
    private const val BISECTIONS = 16
    private const val PATH_STEP = 4f

    // MARK: - Public API

    /** One seeded rock filling exactly `size`. */
    fun stone(
        size: Size,
        seed: String,
        options: StoneOptions = StoneOptions(),
    ): List<Offset> {
        if (size.width <= 0f || size.height <= 0f) return emptyList()
        return memo(Key("stone", size, seed, options)) {
            listOf(rock(size.width.toDouble(), size.height.toDouble(), StoneRandom(seed), options))
        }.first().toOffsets()
    }

    /**
     * The rock filling exactly `width × height`: four unequal superelliptic
     * corners (fullest top-left) joined by gently bowed sides. Takes nine draws
     * from [random].
     */
    fun rockOutline(
        width: Float,
        height: Float,
        random: StoneRandom,
        options: StoneOptions = StoneOptions(),
    ): List<Offset> = rock(width.toDouble(), height.toDouble(), random, options).toOffsets()

    /**
     * One wide rock split by `n - 1` parallel leaning cuts. Each cut leaves a
     * [StoneOptions.gutter] and a [StoneOptions.fillet] on both sides, so the
     * pieces fit back. Pieces are in `size`'s box, left to right.
     */
    fun splitRow(
        size: Size,
        n: Int = 3,
        seed: String = QUICK_ACTIONS_SEED,
        options: StoneOptions = StoneOptions(),
    ): List<List<Offset>> {
        val width = size.width.toDouble()
        val height = size.height.toDouble()
        if (n < 1 || width <= n * options.gutter.toDouble() || height <= 0.0) return List(n.coerceAtLeast(0)) { emptyList() }
        return memo(Key("row", size, seed, options, n = n)) { splitRowPoints(width, height, n, seed, options) }
            .map { it.toOffsets() }
    }

    /**
     * One calm stone over the whole bento, clipped along its straight gutters,
     * in [bentoCells] order. `order` is the three shortcuts' raw values, lead
     * first (iOS `HomeShortcut.bento(lead:)`); each seeds its own corner.
     */
    fun splitBento(
        size: Size,
        leadWidth: Float,
        order: List<String>,
    ): List<List<Offset>> {
        val w = size.width.toDouble()
        val h = size.height.toDouble()
        val lead = leadWidth.toDouble()
        if (order.size != 3 || w <= lead + BENTO_GUTTER || h <= BENTO_GUTTER || lead <= 0.0) return List(3) { emptyList() }
        return memo(Key("bento", size, order.joinToString("|"), StoneOptions(), leadWidth = leadWidth)) {
            splitBentoPoints(w, h, lead, order)
        }.map { it.toOffsets() }
    }

    /** The row's cells: `n` equal columns separated by `gutter`. */
    fun rowCells(
        size: Size,
        n: Int = 3,
        gutter: Float = StoneOptions().gutter,
    ): List<Rect> {
        val w = (size.width - (n - 1) * gutter) / n
        return List(n) { k -> Rect(Offset(k * (w + gutter), 0f), Size(w, size.height)) }
    }

    /** The bento's cells: the lead on the left, the other two stacked beside it. */
    fun bentoCells(
        size: Size,
        leadWidth: Float,
    ): List<Rect> =
        bentoCellRects(size.width.toDouble(), size.height.toDouble(), leadWidth.toDouble()).map {
            Rect(it.minX.toFloat(), it.minY.toFloat(), it.maxX.toFloat(), it.maxY.toFloat())
        }

    /** Evenly spaced points along a closed outline, at least 24. */
    fun resample(
        outline: List<Offset>,
        step: Float,
    ): List<Offset> = resample(outline.map { P(it.x.toDouble(), it.y.toDouble()) }, step.toDouble()).toOffsets()

    /**
     * A smooth closed path through an outline: resampled every 4 units, then
     * drawn as a Catmull-Rom spline (control points at ±1/6). Each point maps
     * to `(point - origin) × scale`, so an outline computed in dp draws in px
     * and a piece can be drawn in its own cell's coordinates.
     */
    fun path(
        outline: List<Offset>,
        scale: Float = 1f,
        origin: Offset = Offset.Zero,
    ): Path {
        val path = Path()
        val p = resample(outline, PATH_STEP).map { (it - origin) * scale }
        val n = p.size
        if (n <= 2) return path
        path.moveTo(p[0].x, p[0].y)
        for (i in 0 until n) {
            val p0 = p[(i - 1 + n) % n]
            val p1 = p[i]
            val p2 = p[(i + 1) % n]
            val p3 = p[(i + 2) % n]
            path.cubicTo(
                p1.x + (p2.x - p0.x) / 6,
                p1.y + (p2.y - p0.y) / 6,
                p2.x - (p3.x - p1.x) / 6,
                p2.y - (p3.y - p1.y) / 6,
                p2.x,
                p2.y,
            )
        }
        path.close()
        return path
    }

    /** Empties the memo. For tests that prove a fresh computation agrees with it. */
    internal fun clearMemo() = synchronized(cache) { cache.clear() }

    // MARK: - Memo

    private data class Key(
        val kind: String,
        val width: Int,
        val height: Int,
        val seed: String,
        val options: StoneOptions,
        val n: Int,
        val leadWidth: Int,
    ) {
        constructor(
            kind: String,
            size: Size,
            seed: String,
            options: StoneOptions,
            n: Int = 0,
            leadWidth: Float = 0f,
        ) : this(
            kind,
            (size.width * 4).roundToInt(),
            (size.height * 4).roundToInt(),
            seed,
            options,
            n,
            (leadWidth * 4).roundToInt(),
        )
    }

    private val cache = HashMap<Key, List<List<P>>>()

    private fun memo(
        key: Key,
        compute: () -> List<List<P>>,
    ): List<List<P>> {
        synchronized(cache) { cache[key]?.let { return it } }
        val pieces = compute()
        synchronized(cache) {
            if (cache.size > MEMO_CAP) cache.clear()
            cache[key] = pieces
        }
        return pieces
    }

    // MARK: - Row: one rock, n pieces

    private fun splitRowPoints(
        width: Double,
        height: Double,
        n: Int,
        seed: String,
        options: StoneOptions,
    ): List<List<P>> {
        val gutter = options.gutter.toDouble()
        val fillet = options.fillet.toDouble()
        val r = StoneRandom(seed)
        val rock = rock(width, height, r, options)
        val leanDraw = r.next()
        val rockSdf = polygonSdf(resample(rock, 4.0))
        val lean = options.lean?.toDouble() ?: (0.11 + jitter(leanDraw, 0.03))
        val wn = (width - (n - 1) * gutter) / n
        val gx = List(n - 1) { k -> (k + 1) * wn + (k + 0.5) * gutter }

        fun cut(
            x: Double,
            side: Double,
        ) = halfSpace(P(x, height / 2), P(lean * height, -height), side, gutter)
        val reach = hypot(width, height)
        return List(n) { k ->
            val cuts =
                buildList {
                    if (k > 0) add(cut(gx[k - 1], -1.0))
                    if (k < n - 1) add(cut(gx[k], 1.0))
                }
            piece(rockSdf, cuts, P(k * (wn + gutter) + wn / 2, height / 2), fillet, reach)
        }
    }

    private fun rock(
        w: Double,
        h: Double,
        r: StoneRandom,
        options: StoneOptions,
    ): List<P> {
        val corners = doubleArrayOf(1.15, 0.95, 0.8, 1.05)
        val bows = doubleArrayOf(1.0, 1.4, 0.4, 1.4)
        val base = options.corner.toDouble()
        val bowScale = options.bow.toDouble()
        val most = min(w, h) / 2
        val e = corners.map { min(most, base * it * (1 + jitter(r.next(), 0.1))) }
        val bow = bows.map { (it + jitter(r.next(), 0.3)) * bowScale }
        val n = 2.5 + jitter(r.next(), 0.15)
        val pts = ArrayList<P>()

        fun side(
            p0: P,
            p1: P,
            normal: P,
            b: Double,
        ) {
            val length = hypot(p1.x - p0.x, p1.y - p0.y)
            val count = max(2, ceil(length / 1.5).toInt())
            val amp = b * min(1.6, length / 70)
            for (i in 0 until count) {
                val t = i.toDouble() / count
                val o = amp * sin(PI * t)
                pts.add(P(p0.x + (p1.x - p0.x) * t + normal.x * o, p0.y + (p1.y - p0.y) * t + normal.y * o))
            }
        }

        fun corner(
            cx: Double,
            cy: Double,
            radius: Double,
            a0: Double,
        ) {
            val count = max(8, ceil(radius).toInt())
            for (i in 0 until count) {
                val th = a0 + (PI / 2) * i / count
                pts.add(P(cx + radius * superellipse(cos(th), n), cy + radius * superellipse(sin(th), n)))
            }
        }
        val (a, b, c, d) = e
        corner(a, a, a, PI)
        side(P(a, 0.0), P(w - b, 0.0), P(0.0, -1.0), bow[0])
        corner(w - b, b, b, -PI / 2)
        side(P(w, b), P(w, h - c), P(1.0, 0.0), bow[1])
        corner(w - c, h - c, c, 0.0)
        side(P(w - c, h), P(d, h), P(0.0, 1.0), bow[2])
        corner(d, h - d, d, PI / 2)
        side(P(0.0, h - d), P(0.0, a), P(-1.0, 0.0), bow[3])
        return fit(pts, R(0.0, 0.0, w, h))
    }

    /** Signed distance to a closed polygon: negative inside. */
    private fun polygonSdf(p: List<P>): (P) -> Double =
        { q ->
            var best = Double.POSITIVE_INFINITY
            var inside = false
            var j = p.size - 1
            for (i in p.indices) {
                val a = p[j]
                val b = p[i]
                val ex = b.x - a.x
                val ey = b.y - a.y
                val wx = q.x - a.x
                val wy = q.y - a.y
                val len2 = ex * ex + ey * ey
                val t = max(0.0, min(1.0, (wx * ex + wy * ey) / (if (len2 == 0.0) 1.0 else len2)))
                val dx = wx - ex * t
                val dy = wy - ey * t
                best = min(best, dx * dx + dy * dy)
                if ((a.y > q.y) != (b.y > q.y) && q.x < (b.x - a.x) * (q.y - a.y) / (b.y - a.y) + a.x) {
                    inside = !inside
                }
                j = i
            }
            (if (inside) -1.0 else 1.0) * sqrt(best)
        }

    /** The side of a gutter line a piece keeps, as a distance field. */
    private fun halfSpace(
        through: P,
        direction: P,
        side: Double,
        gap: Double,
    ): (P) -> Double {
        val l = hypot(direction.x, direction.y)
        val nx = -direction.y / l
        val ny = direction.x / l
        return { q -> side * ((q.x - through.x) * nx + (q.y - through.y) * ny) + gap / 2 }
    }

    /** Intersection of two distance fields, rounded by `r`. */
    private fun roundIntersection(
        a: Double,
        b: Double,
        r: Double,
    ): Double = min(-r, max(a, b)) + hypot(max(r + a, 0.0), max(r + b, 0.0))

    /**
     * The rock minus the gutters, traced by casting rays from the piece's
     * centre (every piece is convex).
     */
    private fun piece(
        rock: (P) -> Double,
        cuts: List<(P) -> Double>,
        centre: P,
        fillet: Double,
        reach: Double,
    ): List<P> {
        fun sdf(q: P): Double = cuts.fold(rock(q)) { acc, cut -> roundIntersection(acc, cut(q), fillet) }
        return List(RAYS) { i ->
            val th = PI * 1.12 + 2 * PI * i / RAYS
            val dx = cos(th)
            val dy = sin(th)
            var lo = 0.0
            var hi = reach
            repeat(BISECTIONS) {
                val mid = (lo + hi) / 2
                if (sdf(P(centre.x + dx * mid, centre.y + dy * mid)) < 0) lo = mid else hi = mid
            }
            P(centre.x + dx * lo, centre.y + dy * lo)
        }
    }

    // MARK: - Bento: one stone, split

    private fun bentoCellRects(
        width: Double,
        height: Double,
        leadWidth: Double,
    ): List<R> {
        val x = leadWidth + BENTO_GUTTER
        val w = width - x
        val h = (height - BENTO_GUTTER) / 2
        return listOf(
            R(0.0, 0.0, leadWidth, height),
            R(x, 0.0, x + w, h),
            R(x, h + BENTO_GUTTER, x + w, h + BENTO_GUTTER + h),
        )
    }

    private fun splitBentoPoints(
        w: Double,
        h: Double,
        leadWidth: Double,
        order: List<String>,
    ): List<List<P>> {
        val rl = StoneRandom(order[0] + BENTO_SEED_SUFFIX)
        val r1 = StoneRandom(order[1] + BENTO_SEED_SUFFIX)
        val r2 = StoneRandom(order[2] + BENTO_SEED_SUFFIX)
        val base = min(leadWidth, h) / 2
        val topLeft = 0.44 + rl.next() * 0.1
        val bottomLeft = 0.44 + rl.next() * 0.1
        val radii = listOf(topLeft, 0.34 + r1.next() * 0.12, 0.34 + r2.next() * 0.12, bottomLeft).map { it * base }
        val n = 2.3 + rl.next() * 0.4

        val raw = ArrayList<P>()

        fun corner(
            cx: Double,
            cy: Double,
            r: Double,
            a0: Double,
        ) {
            for (i in 0..24) {
                val a = a0 + (PI / 2) * i / 24
                raw.add(P(cx + r * superellipse(cos(a), n), cy + r * superellipse(sin(a), n)))
            }
        }
        corner(radii[0], radii[0], radii[0], PI)
        corner(w - radii[1], radii[1], radii[1], -PI / 2)
        corner(w - radii[2], h - radii[2], radii[2], 0.0)
        corner(radii[3], h - radii[3], radii[3], PI / 2)

        // A slow three-lobe breath along the outline.
        var p = resample(raw, 1.5)
        val normal = normals(p)
        val breath = h * (0.008 + rl.next() * 0.01)
        val phase = rl.next() * 2 * PI
        val count = p.size.toDouble()
        p =
            p.indices.map { i ->
                val turn = 6 * PI * i / count
                val d = breath * sin(turn + phase)
                P(p[i].x + normal[i].x * d, p[i].y + normal[i].y * d)
            }
        val stone = resample(fit(p, R(0.0, 0.0, w, h)), 1.0)
        return bentoCellRects(w, h, leadWidth).map { smooth(clip(stone, it)) }
    }

    /** Sutherland–Hodgman against a rectangle. */
    private fun clip(
        polygon: List<P>,
        rect: R,
    ): List<P> {
        class Edge(
            val inside: (P) -> Boolean,
            val cross: (P, P) -> P,
        )

        fun atX(x: Double): (P, P) -> P = { a, b -> P(x, a.y + (b.y - a.y) * (x - a.x) / (b.x - a.x)) }

        fun atY(y: Double): (P, P) -> P = { a, b -> P(a.x + (b.x - a.x) * (y - a.y) / (b.y - a.y), y) }
        val edges =
            listOf(
                Edge({ it.x >= rect.minX }, atX(rect.minX)),
                Edge({ it.x <= rect.maxX }, atX(rect.maxX)),
                Edge({ it.y >= rect.minY }, atY(rect.minY)),
                Edge({ it.y <= rect.maxY }, atY(rect.maxY)),
            )
        var out = polygon
        for (edge in edges) {
            val input = out
            val next = ArrayList<P>()
            for (i in input.indices) {
                val a = input[(i - 1 + input.size) % input.size]
                val b = input[i]
                val aIn = edge.inside(a)
                val bIn = edge.inside(b)
                if (bIn) {
                    if (!aIn) next.add(edge.cross(a, b))
                    next.add(b)
                } else if (aIn) {
                    next.add(edge.cross(a, b))
                }
            }
            if (next.isEmpty()) return emptyList()
            out = next
        }
        return out
    }

    /** Eases the clipped corners: five passes of an 11-point moving average. */
    private fun smooth(polygon: List<P>): List<P> {
        if (polygon.size <= 2) return polygon
        var q = resample(polygon, 1.0)
        val n = q.size
        val window = 5
        repeat(5) {
            val prev = q
            q =
                List(n) { i ->
                    var sx = 0.0
                    var sy = 0.0
                    for (j in -window..window) {
                        val p = prev[((i + j) % n + n) % n]
                        sx += p.x
                        sy += p.y
                    }
                    val k = (2 * window + 1).toDouble()
                    P(sx / k, sy / k)
                }
        }
        return q
    }

    // MARK: - Outline maths

    private fun jitter(
        u: Double,
        amount: Double,
    ): Double = (u - 0.5) * 2 * amount

    private fun superellipse(
        v: Double,
        n: Double,
    ): Double = if (v == 0.0) 0.0 else sign(v) * abs(v).pow(2 / n)

    /** Stretches an outline so its bounds are exactly `rect`. */
    private fun fit(
        p: List<P>,
        rect: R,
    ): List<P> {
        if (p.isEmpty()) return p
        val minX = p.minOf { it.x }
        val maxX = p.maxOf { it.x }
        val minY = p.minOf { it.y }
        val maxY = p.maxOf { it.y }
        if (maxX <= minX || maxY <= minY) return p
        val width = rect.maxX - rect.minX
        val height = rect.maxY - rect.minY
        return p.map {
            P(rect.minX + (it.x - minX) / (maxX - minX) * width, rect.minY + (it.y - minY) / (maxY - minY) * height)
        }
    }

    private fun resample(
        p: List<P>,
        step: Double,
    ): List<P> {
        val n = p.size
        if (n <= 2) return p
        val cum = DoubleArray(n + 1)
        for (i in 1..n) {
            val a = p[i - 1]
            val b = p[i % n]
            cum[i] = cum[i - 1] + hypot(b.x - a.x, b.y - a.y)
        }
        val length = cum[n]
        val count = max(24, (length / step).roundToInt())
        val out = ArrayList<P>(count)
        var j = 0
        for (i in 0 until count) {
            val s = length * i / count
            while (j < n - 1 && cum[j + 1] < s) j++
            val a = p[j]
            val b = p[(j + 1) % n]
            val seg = cum[j + 1] - cum[j]
            val u = if (seg == 0.0) 0.0 else (s - cum[j]) / seg
            out.add(P(a.x + (b.x - a.x) * u, a.y + (b.y - a.y) * u))
        }
        return out
    }

    /** Outward unit normals, from the points two steps either side. */
    private fun normals(p: List<P>): List<P> {
        val n = p.size
        val cx = p.fold(0.0) { acc, it -> acc + it.x } / n
        val cy = p.fold(0.0) { acc, it -> acc + it.y } / n
        return p.indices.map { i ->
            val a = p[(i - 2 + n) % n]
            val b = p[(i + 2) % n]
            var nx = b.y - a.y
            var ny = -(b.x - a.x)
            val l = hypot(nx, ny)
            if (l > 0) {
                nx /= l
                ny /= l
            }
            if (nx * (p[i].x - cx) + ny * (p[i].y - cy) < 0) {
                nx = -nx
                ny = -ny
            }
            P(nx, ny)
        }
    }

    private fun List<P>.toOffsets(): List<Offset> = map { Offset(it.x.toFloat(), it.y.toFloat()) }

    /** A point in `Double`, as iOS's `CGPoint` on 64-bit. */
    private data class P(
        val x: Double,
        val y: Double,
    )

    private data class R(
        val minX: Double,
        val minY: Double,
        val maxX: Double,
        val maxY: Double,
    )
}

package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.RevealMotion
import app.pbbls.android.core.designsystem.SpineColors
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.ui.gem.FacetKind
import app.pbbls.android.core.ui.gem.GemGeometry
import app.pbbls.android.core.ui.gem.GemPoint
import app.pbbls.android.core.ui.render.stone.StoneTones
import app.pbbls.android.features.path.wrap.intro.IntroKey
import app.pbbls.android.features.path.wrap.intro.IntroLanding
import app.pbbls.android.features.path.wrap.intro.IntroMotion
import app.pbbls.android.features.path.wrap.intro.IntroReveal
import app.pbbls.android.features.path.wrap.intro.IntroScore
import app.pbbls.android.features.path.wrap.intro.counted
import app.pbbls.android.features.path.wrap.intro.introReveal
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private val HEIGHT = 266.dp
private const val SCALE = 1.14f
private const val CENTRE_Y = 144f
private val SEAM = 0.9.dp
private val EDGE = 0.35.dp
private val TICK = 0.9.dp
private const val TICK_FROM = 1.5f
private const val TICK_TO = 5.5f
private const val LABEL_GAP = 9f
private const val LABEL_PAD = 5f
private const val SPREAD_STEP = 0.008
private const val SPREAD_TRIES = 600
private val RAMP_AT = floatArrayOf(0f, 0.3f, 0.62f, 1f)

/** One family's name and share beside the stone. */
internal class GemLabel(
    val slug: String,
    val name: String,
    val percent: Int,
)

/**
 * The emotions gem drawn (#1207, D10): the fixed faithful brilliant ([GemGeometry]), each facet in
 * its family's ramp at the facet's own light, an uncoloured facet in the page's neutral, every edge
 * a seam of the page ("uniform"), the table a window of paper with the count, and each family named
 * just outside its run, pushed apart until no two names touch. The allocation is the server's
 * [facets]; this only colours it.
 *
 * In the month intro (#1242, [reveal]) the blank stone fades in and its count rises, running up from 0
 * to [count]; then each facet takes its colour over the table's paper, and each family's name and share
 * rise with its run's first facet. Settled, it draws as ever.
 */
@Composable
internal fun GemCanvas(
    facets: Map<String, String?>,
    paletteOf: (String) -> EmotionPalette?,
    labels: List<GemLabel>,
    count: Int,
    countLabel: String,
    description: String,
    modifier: Modifier = Modifier,
    reveal: IntroReveal = IntroReveal.settled,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val fills = remember(facets, colors, paletteOf) { facetFills(facets, paletteOf, colors) }
    val measurer = rememberTextMeasurer()

    Spacer(
        modifier
            .fillMaxWidth()
            .height(HEIGHT)
            // The blank stone fades in, its seams, table and ticks still (iOS `GemStone`'s opacity); the count rises in the draw.
            .introReveal(reveal, IntroKey.Blank, IntroLanding.FADE)
            .semantics { contentDescription = description }
            .drawWithCache {
                // Built once per size and composition: a frame of the intro only reads the reveal and draws.
                val sc = SCALE * density
                val cx = size.width / 2
                val cy = CENTRE_Y * density

                fun at(q: GemPoint) = Offset(cx + q.x.toFloat() * sc, cy + q.y.toFloat() * sc)

                fun path(points: List<GemPoint>) =
                    Path().apply {
                        points.forEachIndexed { i, q ->
                            if (i == 0) moveTo(at(q).x, at(q).y) else lineTo(at(q).x, at(q).y)
                        }
                        close()
                    }

                val cut =
                    GemGeometry.facets.filter { it.kind != FacetKind.TABLE }.map { f ->
                        CutFacet(IntroKey.Facet(f.id), path(f.points), fills.getValue(f.id))
                    }
                val seams = GemGeometry.edges.filter { "table" !in it.facets.toList() }.map { at(it.a) to at(it.b) }
                val table = path(GemGeometry.byId.getValue("table").points)
                // The table's paper, which a facet shows until it lights.
                val paper = if (colors.isNight) StoneTones.mixOklab(colors.ink, 0.07f, colors.stock) else colors.stock
                // Each facet edged in its own colour, so neighbours meet without a hairline gap.
                val edge = Stroke(EDGE.toPx(), join = StrokeJoin.Round)
                val seam = SEAM.toPx()
                val rim = Stroke(SEAM.toPx(), join = StrokeJoin.Round)
                val numeral = type.dayNumeral.copy(color = colors.ink)
                val n = measurer.measure(count.toString(), numeral)
                val l = measurer.measure(countLabel, type.dayWeekday.copy(color = colors.ink2))
                val names =
                    placeLabels(
                        labels,
                        facets,
                        labels.map {
                            measurer.measure(it.name, type.soulName.copy(color = colors.ink)) to
                                measurer.measure("${it.percent} %", type.contentsTime.copy(color = colors.ink2))
                        },
                        cx,
                        cy,
                        sc,
                    )
                val rise = IntroMotion.riseOffset.toPx()

                onDrawBehind {
                    cut.forEach { f ->
                        // A facet no family owns has no beat: it reads 1 and lands with the stone (D2).
                        val lit = reveal(f.key)
                        val fill =
                            if (lit <
                                1f
                            ) {
                                StoneTones.mixSrgb(f.fill, RevealMotion.easeOut(lit.toDouble()).toFloat(), paper)
                            } else {
                                f.fill
                            }
                        drawPath(f.path, fill)
                        drawPath(f.path, fill, style = edge)
                    }
                    seams.forEach { (a, b) -> drawLine(colors.ground, a, b, seam, StrokeCap.Round) }
                    drawPath(table, paper)
                    drawPath(table, colors.ground, style = rim)
                    // From 0, counting up to the pebbles on its beat; settled, the number as ever.
                    val counting = reveal(IntroKey.Count)
                    val shown =
                        if (counting < 1f) {
                            measurer.measure(IntroScore.counted(count, counting.toDouble()).toString(), numeral)
                        } else {
                            n
                        }
                    // The count and its label also rise with the blank stone (iOS: each its own `.introReveal(.blank)`);
                    // the layer has already faded them.
                    val blank = reveal(IntroKey.Blank)
                    if (blank < 1f) {
                        translate(top = rise * IntroMotion.riseLift(blank.toDouble()).toFloat()) { drawCount(shown, n, l, cx, cy) }
                    } else {
                        drawCount(shown, n, l, cx, cy)
                    }
                    drawLabels(names, reveal, rise, cx, cy, sc, colors)
                }
            },
    )
}

/** The count in the table and its label under it, placed by the settled count's height so they hold still as it counts. */
private fun DrawScope.drawCount(
    shown: TextLayoutResult,
    settled: TextLayoutResult,
    label: TextLayoutResult,
    cx: Float,
    cy: Float,
) {
    drawText(shown, topLeft = Offset(cx - shown.size.width / 2f, cy - settled.size.height * 0.62f))
    drawText(label, topLeft = Offset(cx - label.size.width / 2f, cy + settled.size.height * 0.36f))
}

/** A facet of the cut, laid out: its beat's key, its path at this size, its settled fill. */
private class CutFacet(
    val key: IntroKey,
    val path: Path,
    val fill: Color,
)

private class LabelPlace(
    val key: IntroKey,
    val anchor: Double,
    var angle: Double,
    val name: TextLayoutResult,
    val pct: TextLayoutResult,
) {
    var x0 = 0f
    var y0 = 0f
    val w get() = max(name.size.width, pct.size.width).toFloat()
    val h get() = (name.size.height + pct.size.height).toFloat()
    var align = 0
}

/** Names just outside each run, nudged along the rim until no two overlap. Once per size: the spread is far too slow for a frame. */
private fun CacheDrawScope.placeLabels(
    labels: List<GemLabel>,
    facets: Map<String, String?>,
    texts: List<Pair<TextLayoutResult, TextLayoutResult>>,
    cx: Float,
    cy: Float,
    sc: Float,
): List<LabelPlace> {
    val runs =
        labels.mapIndexedNotNull { i, label ->
            val ids = GemGeometry.allocated.filter { facets[it] == label.slug }
            if (ids.isEmpty()) return@mapIndexedNotNull null
            val a = GemGeometry.runAngle(ids)
            LabelPlace(IntroKey.Label(label.slug), a, a, texts[i].first, texts[i].second)
        }
    val gap = LABEL_GAP * density
    val pad = LABEL_PAD * density

    fun place(r: LabelPlace) {
        val radius = GemGeometry.rimRadius(r.angle).toFloat() * sc + gap
        val c = cos(r.angle).toFloat()
        val s = sin(r.angle).toFloat()
        val ax = cx + radius * c
        val ay = cy + radius * s
        r.x0 = (ax - (1 - c) / 2 * r.w).coerceIn(0f, max(0f, size.width - r.w))
        r.y0 = ay - (1 - s) / 2 * r.h
        r.align =
            if (c > 0.25f) {
                -1
            } else if (c < -0.25f) {
                1
            } else {
                0
            }
    }

    fun hit(
        p: LabelPlace,
        q: LabelPlace,
    ) = p.x0 < q.x0 + q.w + pad && q.x0 < p.x0 + p.w + pad && p.y0 < q.y0 + q.h + pad / 2 && q.y0 < p.y0 + p.h + pad / 2
    // Until no name moves (iOS breaks there too): a pass that moves none leaves every angle, so every later pass, as it was.
    for (attempt in 0 until SPREAD_TRIES) {
        runs.forEach(::place)
        var moved = false
        for (i in runs.indices) {
            for (j in i + 1 until runs.size) {
                val p = runs[i]
                val q = runs[j]
                if (!hit(p, q)) continue
                val d = atan2(sin(q.angle - p.angle), cos(q.angle - p.angle))
                val sign = if (d >= 0) 1 else -1
                p.angle -= sign * SPREAD_STEP
                q.angle += sign * SPREAD_STEP
                moved = true
            }
        }
        if (!moved) break
    }
    runs.forEach(::place)
    return runs
}

/**
 * A hairline tick ties each name to its run, with the stone; the name and its share rise with the run's
 * first facet in the intro (#1242), and stand still once settled.
 */
private fun DrawScope.drawLabels(
    runs: List<LabelPlace>,
    reveal: IntroReveal,
    rise: Float,
    cx: Float,
    cy: Float,
    sc: Float,
    colors: SpineColors,
) {
    runs.forEach { r ->
        val radius = GemGeometry.rimRadius(r.anchor).toFloat() * sc
        val c = cos(r.anchor).toFloat()
        val s = sin(r.anchor).toFloat()
        drawLine(
            colors.ink3,
            Offset(cx + (radius + TICK_FROM * density) * c, cy + (radius + TICK_FROM * density) * s),
            Offset(cx + (radius + TICK_TO * density) * c, cy + (radius + TICK_TO * density) * s),
            TICK.toPx(),
            StrokeCap.Round,
        )

        fun xFor(t: TextLayoutResult) =
            when (r.align) {
                -1 -> r.x0
                1 -> r.x0 + r.w - t.size.width
                else -> r.x0 + (r.w - t.size.width) / 2
            }
        // Settled: no offset, and the text's own alpha (NaN is drawText's default).
        val lit = reveal(r.key)
        val eased = if (lit < 1f) RevealMotion.easeOut(lit.toDouble()).toFloat() else 1f
        val dy = if (lit < 1f) rise * IntroMotion.riseLift(lit.toDouble()).toFloat() else 0f
        val alpha = if (lit < 1f) eased else Float.NaN
        drawText(r.name, topLeft = Offset(xFor(r.name), r.y0 + dy), alpha = alpha)
        drawText(r.pct, topLeft = Offset(xFor(r.pct), r.y0 + r.name.size.height + dy), alpha = alpha)
    }
}

/** Every facet's flat colour: its family's four-stop ramp at the facet's tone, or the neutral pushed toward the ground. */
private fun facetFills(
    facets: Map<String, String?>,
    paletteOf: (String) -> EmotionPalette?,
    colors: SpineColors,
): Map<String, Color> {
    val night = colors.isNight

    // mix(a, b, t) in the board's sense: from a toward b by t.
    fun mix(
        a: Color,
        b: Color,
        t: Float,
    ) = StoneTones.mixOklab(b, t, a)
    val neutral =
        if (night) {
            listOf(
                mix(colors.stock, colors.ink, 0.30f),
                mix(colors.stock, colors.ink, 0.19f),
                mix(colors.stock, colors.ink, 0.11f),
                mix(colors.ground, colors.ink, 0.05f),
            )
        } else {
            listOf(
                colors.stock,
                mix(colors.ground, colors.ink, 0.05f),
                mix(colors.ground, colors.ink, 0.12f),
                mix(colors.ground, colors.ink, 0.22f),
            )
        }
    val stops = HashMap<String, List<Color>>()

    fun stopsOf(slug: String): List<Color>? =
        stops.getOrPut(slug) {
            val p = paletteOf(slug) ?: return null
            if (night) {
                listOf(
                    mix(p.light, p.secondary, 0.35f),
                    p.secondary,
                    p.primary,
                    mix(p.primary, colors.ground, 0.42f),
                )
            } else {
                listOf(mix(p.light, p.secondary, 0.15f), p.secondary, p.primary, p.shaded)
            }
        }

    fun ramp(
        st: List<Color>,
        tone: Float,
    ): Color {
        val t = tone.coerceIn(0f, 1f)
        for (i in 1 until RAMP_AT.size) {
            if (t <= RAMP_AT[i]) return mix(st[i - 1], st[i], (t - RAMP_AT[i - 1]) / (RAMP_AT[i] - RAMP_AT[i - 1]))
        }
        return st.last()
    }
    return GemGeometry.facets.filter { it.kind != FacetKind.TABLE }.associate { f ->
        val tone = f.tone.toFloat()
        val family = facets[f.id]?.let(::stopsOf)
        f.id to (if (family != null) ramp(family, tone) else mix(ramp(neutral, tone), colors.ground, 0.45f))
    }
}

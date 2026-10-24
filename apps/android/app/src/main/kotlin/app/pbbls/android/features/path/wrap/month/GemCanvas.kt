package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineColors
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.ui.gem.FacetKind
import app.pbbls.android.core.ui.gem.GemGeometry
import app.pbbls.android.core.ui.gem.GemPoint
import app.pbbls.android.core.ui.render.stone.StoneTones
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
 */
@Composable
internal fun GemCanvas(
    facets: Map<String, String?>,
    paletteOf: (String) -> EmotionPalette?,
    labels: List<GemLabel>,
    count: String,
    countLabel: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val fills = remember(facets, colors, paletteOf) { facetFills(facets, paletteOf, colors) }
    val measurer = rememberTextMeasurer()

    Canvas(modifier.fillMaxWidth().height(HEIGHT).semantics { contentDescription = description }) {
        val sc = SCALE * density
        val cx = size.width / 2
        val cy = CENTRE_Y * density

        fun at(q: GemPoint) = Offset(cx + q.x.toFloat() * sc, cy + q.y.toFloat() * sc)

        fun path(points: List<GemPoint>) =
            Path().apply {
                points.forEachIndexed { i, q ->
                    if (i ==
                        0
                    ) {
                        moveTo(at(q).x, at(q).y)
                    } else {
                        lineTo(at(q).x, at(q).y)
                    }
                }
                ; close()
            }

        GemGeometry.facets.filter { it.kind != FacetKind.TABLE }.forEach { f ->
            val p = path(f.points)
            val fill = fills.getValue(f.id)
            drawPath(p, fill)
            // Each facet edged in its own colour, so neighbours meet without a hairline gap.
            drawPath(p, fill, style = Stroke(EDGE.toPx(), join = StrokeJoin.Round))
        }
        GemGeometry.edges.filter { "table" !in it.facets.toList() }.forEach { e ->
            drawLine(colors.ground, at(e.a), at(e.b), SEAM.toPx(), StrokeCap.Round)
        }
        val table = path(GemGeometry.byId.getValue("table").points)
        drawPath(table, if (colors.isNight) StoneTones.mixOklab(colors.ink, 0.07f, colors.stock) else colors.stock)
        drawPath(table, colors.ground, style = Stroke(SEAM.toPx(), join = StrokeJoin.Round))
        val n = measurer.measure(count, type.dayNumeral.copy(color = colors.ink))
        val l = measurer.measure(countLabel, type.dayWeekday.copy(color = colors.ink2))
        drawText(n, topLeft = Offset(cx - n.size.width / 2f, cy - n.size.height * 0.62f))
        drawText(l, topLeft = Offset(cx - l.size.width / 2f, cy + n.size.height * 0.36f))

        drawLabels(
            labels,
            facets,
            measurer.let { m ->
                labels.map {
                    m.measure(it.name, type.soulName.copy(color = colors.ink)) to
                        m.measure("${it.percent} %", type.contentsTime.copy(color = colors.ink2))
                }
            },
            cx,
            cy,
            sc,
            colors,
        )
    }
}

private class LabelPlace(
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

/** Names just outside each run, nudged along the rim until no two overlap; a hairline tick ties each to its run. */
private fun DrawScope.drawLabels(
    labels: List<GemLabel>,
    facets: Map<String, String?>,
    texts: List<Pair<TextLayoutResult, TextLayoutResult>>,
    cx: Float,
    cy: Float,
    sc: Float,
    colors: SpineColors,
) {
    val runs =
        labels.mapIndexedNotNull { i, label ->
            val ids = GemGeometry.allocated.filter { facets[it] == label.slug }
            if (ids.isEmpty()) return@mapIndexedNotNull null
            val a = GemGeometry.runAngle(ids)
            LabelPlace(a, a, texts[i].first, texts[i].second)
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
    repeat(SPREAD_TRIES) {
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
        if (!moved) return@repeat
    }
    runs.forEach(::place)
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
        drawText(r.name, topLeft = Offset(xFor(r.name), r.y0))
        drawText(r.pct, topLeft = Offset(xFor(r.pct), r.y0 + r.name.size.height))
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

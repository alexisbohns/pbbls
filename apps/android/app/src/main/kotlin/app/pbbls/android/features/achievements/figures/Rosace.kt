package app.pbbls.android.features.achievements.figures

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.achievements.EmotionSeat
import app.pbbls.android.core.model.achievements.EmotionsSection
import app.pbbls.android.core.ui.ReferenceStrings
import app.pbbls.android.core.ui.ReferenceType
import app.pbbls.android.features.achievements.AchievementLookups

private const val FACET_SEAM = 0.9f
private const val FRESH_DOT_R = 2.6f
private const val CENTRE_COUNT_RISE = 3f
private const val CENTRE_OF_DROP = 15f

private fun Path.polygon(points: List<Offset>): Path =
    apply {
        points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        close()
    }

/** One seat as the rosace lights it: its run, and its place in that run (the tone's index). */
private class Facet(
    val seat: EmotionSeat,
    val run: FamilyRun,
    val inRun: Int,
)

/** The seats in the server's order, each with its family run and its place in it. */
private fun facets(
    seats: List<EmotionSeat>,
    runs: List<FamilyRun>,
): List<Facet> {
    val out = ArrayList<Facet>(seats.size)
    var i = 0
    runs.forEach { run ->
        for (j in 0 until run.size) {
            out += Facet(seats[i], run, j)
            i += 1
        }
    }
    return out
}

/**
 * The emotions rosace (the board's `gem`, A1 pick 6): one kite per seat, placed by its served
 * index, in the server's order. A met seat is lit in its family's tone, and an unknown family in
 * ink. A new seat carries a dot outside the rim. [labels] names the families around it; [centre]
 * is the count and its "of 38".
 */
@Composable
fun Rosace(
    emotions: EmotionsSection,
    lookups: AchievementLookups,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 270.dp,
    labels: Boolean = true,
    centre: Pair<String, String>? = null,
    ticks: Boolean = true,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val night = colors.isNight
    val seats = emotions.seats
    val runs = remember(seats) { familyRuns(seats) }
    val lit = remember(seats, runs) { facets(seats, runs) }
    val g = remember(size, labels, seats.size) { RosaceGeometry(size.value, labels, seats.size) }
    val names =
        runs.map { run ->
            run.family?.let { slug ->
                ReferenceStrings.referenceName(
                    ReferenceType.EMOTION_CATEGORY,
                    slug,
                    lookups.family(slug)?.name ?: slug,
                )
            }
        }
    val measurer = rememberTextMeasurer()

    Canvas(
        modifier
            .widthIn(max = size)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val k = this.size.width / g.size
        withTransform({ scale(k, k, Offset.Zero) }) {
            drawPath(Path().polygon(g.silhouette()), colors.stock)
            drawPath(Path().polygon(g.silhouette()), colors.ink2, style = Stroke(1f, join = StrokeJoin.Round))
            seats.forEach { s ->
                drawPath(Path().polygon(g.kite(s.seat)), colors.ink2, style = Stroke(1f, join = StrokeJoin.Round))
                drawPath(Path().polygon(g.star(s.seat)), colors.ink2, style = Stroke(1f, join = StrokeJoin.Round))
            }
            lit.forEachIndexed { i, facet ->
                if (!facet.seat.met) return@forEachIndexed
                val run = facet.run
                val palette = run.family?.let { lookups.family(it)?.palette }
                val at = facet.seat.seat
                val tone = palette?.let { seatTone(it, facet.inRun, run.size, night, colors.stock, soft = false) } ?: colors.ink
                drawPath(Path().polygon(g.kite(at)), tone)
                drawPath(Path().polygon(g.kite(at)), colors.stock, style = Stroke(FACET_SEAM))
                // The star to the next seat is lit when that seat is met in the same run.
                val next = lit.getOrNull(i + 1)
                if (next != null && next.run === run && next.seat.met) {
                    val soft = palette?.let { seatTone(it, facet.inRun + 1, run.size, night, colors.stock, soft = true) } ?: colors.ink2
                    drawPath(Path().polygon(g.star(at)), soft)
                    drawPath(Path().polygon(g.star(at)), colors.stock, style = Stroke(FACET_SEAM))
                }
            }
            if (ticks) {
                runs.forEach { run ->
                    val (a, b) = g.tick(run.start)
                    drawLine(colors.ink2, a, b, 1f, StrokeCap.Round)
                }
            }
            seats.forEach { s -> if (s.isNew) drawCircle(colors.ink, FRESH_DOT_R, g.freshDot(s.seat)) }
        }
        if (labels) {
            runs.forEachIndexed { i, run ->
                val name = names[i] ?: return@forEachIndexed
                val palette = run.family?.let { lookups.family(it)?.palette }
                val colour = palette?.let { familyLabelColor(run.family, it, night) } ?: colors.ink2
                val layout = measurer.measure(name, type.rosaceFamily.copy(color = colour))
                val at = g.label(run.start, run.size) * k
                drawText(layout, topLeft = at - Offset(layout.size.width / 2f, layout.size.height / 2f))
            }
        }
        centre?.let { (count, of) ->
            val countLayout = measurer.measure(count, type.rosaceCount.copy(color = colors.ink))
            val ofLayout = measurer.measure(of, type.rosaceOf.copy(color = colors.ink2))
            val c = Offset(g.centre, g.centre) * k
            drawText(countLayout, topLeft = c - Offset(countLayout.size.width / 2f, countLayout.size.height - CENTRE_COUNT_RISE * k))
            drawText(ofLayout, topLeft = c + Offset(-ofLayout.size.width / 2f, CENTRE_OF_DROP * k - ofLayout.size.height))
        }
    }
}

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

// The board's text, in board units of its 270 box: the count's baseline 3 above the centre, its
// "of 38" 15 below; the count 28 tall, the "of" and the family names 11.
private const val CENTRE_COUNT_RISE = 3f
private const val CENTRE_OF_DROP = 15f
private const val COUNT_SIZE = 28f
private const val SMALL_SIZE = 11f
private const val TEXT_CACHE = 16

private fun Path.polygon(points: List<Offset>): Path =
    apply {
        points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        close()
    }

/** The rosace's polygons for one geometry: its silhouette, a kite and a star per seat. */
private class RosaceCut(
    g: RosaceGeometry,
) {
    val silhouette: Path = Path().polygon(g.silhouette())
    val kites: List<Path> = (0 until g.seats).map { Path().polygon(g.kite(it)) }
    val stars: List<Path> = (0 until g.seats).map { Path().polygon(g.star(it)) }
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
 * The emotions rosace (the board's `gem`, A1 pick 6): one kite per seat, placed by its position
 * in the server's list. A met seat is lit in its family's tone, and an unknown family in
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
    // The cut, built once per geometry in board units: drawn under the figure's scale.
    val cut = remember(g) { RosaceCut(g) }
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
    val measurer = rememberTextMeasurer(cacheSize = TEXT_CACHE)

    Canvas(
        modifier
            .widthIn(max = size)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val k = this.size.width / g.size
        withTransform({ scale(k, k, Offset.Zero) }) {
            drawPath(cut.silhouette, colors.stock)
            drawPath(cut.silhouette, colors.ink2, style = Stroke(1f, join = StrokeJoin.Round))
            seats.indices.forEach { i ->
                drawPath(cut.kites[i], colors.ink2, style = Stroke(1f, join = StrokeJoin.Round))
                drawPath(cut.stars[i], colors.ink2, style = Stroke(1f, join = StrokeJoin.Round))
            }
            lit.forEachIndexed { i, facet ->
                if (!facet.seat.met) return@forEachIndexed
                val run = facet.run
                val palette = run.family?.let { lookups.family(it)?.palette }
                val at = i
                val tone = palette?.let { seatTone(it, facet.inRun, run.size, night, colors.stock, soft = false) } ?: colors.ink
                drawPath(cut.kites[at], tone)
                drawPath(cut.kites[at], colors.stock, style = Stroke(FACET_SEAM))
                // The star to the next seat is lit when that seat is met in the same run.
                val next = lit.getOrNull(i + 1)
                if (next != null && next.run === run && next.seat.met) {
                    // An unresolved family lights in ink, its soft star in ink softened like any other (iOS parity).
                    val soft =
                        palette?.let { seatTone(it, facet.inRun + 1, run.size, night, colors.stock, soft = true) }
                            ?: softTone(colors.ink, colors.stock)
                    drawPath(cut.stars[at], soft)
                    drawPath(cut.stars[at], colors.stock, style = Stroke(FACET_SEAM))
                }
            }
            if (ticks) {
                runs.forEach { run ->
                    val (a, b) = g.tick(run.start)
                    drawLine(colors.ink2, a, b, 1f, StrokeCap.Round)
                }
            }
            seats.forEachIndexed { i, s -> if (s.isNew) drawCircle(colors.ink, FRESH_DOT_R, g.freshDot(i)) }
        }
        if (labels) {
            runs.forEachIndexed { i, run ->
                val name = names[i] ?: return@forEachIndexed
                val palette = run.family?.let { lookups.family(it)?.palette }
                val colour = palette?.let { familyLabelColor(run.family, it, night) } ?: colors.ink
                val style = type.rosaceFamily.copy(color = colour)
                val layout = measurer.measure(name, style)
                drawFigureTextCentred(layout, g.label(run.start, run.size) * k, figureTextScale(style, SMALL_SIZE, k))
            }
        }
        centre?.let { (count, of) ->
            val countStyle = type.rosaceCount.copy(color = colors.ink)
            val ofStyle = type.rosaceOf.copy(color = colors.ink2)
            val c = g.centre * k
            drawFigureText(measurer.measure(count, countStyle), c, c - CENTRE_COUNT_RISE * k, figureTextScale(countStyle, COUNT_SIZE, k))
            drawFigureText(measurer.measure(of, ofStyle), c, c + CENTRE_OF_DROP * k, figureTextScale(ofStyle, SMALL_SIZE, k))
        }
    }
}

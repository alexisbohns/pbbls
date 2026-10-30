package app.pbbls.android.features.achievements.figures

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.model.achievements.DomainsSection
import app.pbbls.android.core.model.achievements.EmotionsSection
import app.pbbls.android.core.model.achievements.ExplorerRing
import app.pbbls.android.core.model.achievements.ExplorerSection
import app.pbbls.android.core.ui.render.stone.InkPrint
import app.pbbls.android.core.ui.render.stone.SealVariant
import app.pbbls.android.core.ui.render.stone.inkPrint
import app.pbbls.android.features.achievements.AchievementLookups
import app.pbbls.android.features.achievements.figures.ExplorerWheelGeometry as G

private const val ARC_ALPHA = 0.5f
private const val ARC_WIDTH = 0.8f
private const val FIRST_FIVE_WIDTH = 10f
private const val CENTRE_GHOST_R = 6f

/**
 * The Explorer wheel (D6; the board's `wheel`, pick 5): five rings around the first pebble. A ring
 * the server lists in `rings_done` draws its track in secondary ink, otherwise in the rule. Every
 * "not yet" is a ghost, and every new item a ring and a dot. The first five and the six firsts sit
 * by their place in the server's list; one this client does not know is a ghost in its place. One
 * node: [description].
 */
@Composable
fun ExplorerWheel(
    explorer: ExplorerSection,
    emotions: EmotionsSection?,
    domains: DomainsSection?,
    lookups: AchievementLookups,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 300.dp,
) {
    val colors = PebblesTheme.spine.colors
    val night = colors.isNight
    val ink = rememberInkSeals(colors.ink, colors.stock, SealVariant.TINY)
    val seats = emotions?.seats.orEmpty()
    val seatKeys =
        seats.mapNotNull { s ->
            val pebble = s.pebble ?: return@mapNotNull null
            val palette = s.family?.let { lookups.family(it)?.palette } ?: return@mapNotNull null
            if (s.met) SealKey(pebble.valence, palette) else null
        }
    val emotionSeals = rememberEmotionSeals(seatKeys, colors.stock, SealVariant.TINY)
    val centre = explorer.firstPebble
    val centrePalette = centre?.emotionId?.let { lookups.emotion(it)?.palette }
    val centreSeals =
        rememberEmotionSeals(
            listOfNotNull(
                centrePalette?.let { p ->
                    centre?.let { SealKey(it.valence, p) }
                },
            ),
            colors.stock,
            SealVariant.MINI,
        )
    val centreInk = rememberInkSeals(colors.ink, colors.stock, SealVariant.MINI)
    val arcs = remember(emotions) { emotions?.let { G.familyArcs(familyRuns(it.seats), it.seats.size) }.orEmpty() }
    val band = remember(domains) { domains?.let(::domainBandItems).orEmpty() }

    // As wide as the room allows, up to [size], always square: the card and the sheet never overflow a narrow phone.
    Canvas(
        modifier
            .widthIn(max = size)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val unit = this.size.width / G.VIEW
        val c = center

        fun at(
            r: Float,
            deg: Float,
        ): Offset = c + G.polar(r, deg) * unit

        fun track(
            r: Float,
            done: Boolean,
        ) = drawCircle(if (done) colors.ink2 else colors.rule, r * unit, c, style = Stroke(unit))

        // The emotions, on the rosace's seats: each family's arc in its hue, a tick where it starts.
        arcs.forEach { arc ->
            val hue = arc.family?.let { lookups.family(it)?.palette?.hue(night) } ?: colors.ink
            val r = G.EMOTIONS_R * unit
            drawArc(
                color = hue,
                startAngle = arc.startDeg - 90f,
                sweepAngle = arc.sweepDeg,
                useCenter = false,
                topLeft = c - Offset(r, r),
                size = Size(2 * r, 2 * r),
                alpha = ARC_ALPHA,
                style = Stroke(ARC_WIDTH * unit),
            )
            drawLine(
                colors.ink2,
                at(G.EMOTIONS_R + G.TICK_IN, arc.tickDeg),
                at(G.EMOTIONS_R + G.TICK_OUT, arc.tickDeg),
                unit,
                StrokeCap.Round,
            )
        }
        seats.forEach { s ->
            val p = at(G.EMOTIONS_R, G.seatAngle(s.seat, seats.size))
            val pebble = s.pebble
            if (!s.met || pebble == null) {
                ghost(p, G.GHOST_R * unit, unit, colors)
                return@forEach
            }
            val w = emotionSealWidth(pebble.valence.intensity) * unit
            val palette = s.family?.let { lookups.family(it)?.palette }
            val print =
                palette?.let { emotionSeals[SealKey(pebble.valence, it)] }
                    ?: ink.of(sizeOf(pebble.valence.intensity), pebble.valence.polarity.inkPrint)
            sealAt(print, p, w)
            if (s.isNew) newMark(p, w, unit, colors.ink)
        }

        // The domains, in the order they came (the served list reversed), then the not yet.
        track(G.DOMAINS_R, ExplorerRing.DOMAINS in explorer.ringsDone)
        val of = domains?.of?.takeIf { it > 0 } ?: band.size
        band.forEachIndexed { i, item ->
            val p = at(G.DOMAINS_R, G.domainAngle(i, of))
            if (!item.on) {
                ghost(p, G.GHOST_R * unit, unit, colors)
            } else {
                sealAt(ink.of(ValenceSizeGroup.SMALL, InkPrint.RELIEF), p, G.DOMAIN_WIDTH * unit)
                if (item.isNew) newMark(p, G.DOMAIN_WIDTH * unit, unit, colors.ink)
            }
        }

        // 25 pebbles: the server's count (capped at 25) lights that many seats.
        track(G.TWENTY_FIVE_R, ExplorerRing.TWENTY_FIVE in explorer.ringsDone)
        val kept = explorer.twentyFive?.count ?: 0
        for (i in 0 until TWENTY_FIVE_SEATS) {
            val p = at(G.TWENTY_FIVE_R, G.twentyFiveAngle(i))
            if (i < kept) {
                sealAt(ink.of(ValenceSizeGroup.SMALL, InkPrint.RELIEF), p, G.TWENTY_FIVE_WIDTH * unit)
            } else {
                ghost(p, G.SMALL_GHOST_R * unit, unit, colors)
            }
        }

        // The six firsts: three intensities, three valences, as served.
        track(G.SIX_R, ExplorerRing.SIX_FIRSTS in explorer.ringsDone)
        val sixes = explorer.sixFirsts
        sixes.forEachIndexed { i, first ->
            val p = at(G.SIX_R, G.sixAngle(i, sixes.size))
            val kind = first.kind
            if (first.done && kind != null) sixFirstMark(kind, p, unit, ink) else ghost(p, G.GHOST_R * unit, unit, colors)
            if (first.isNew) newMark(p, (kind?.let { SIX_MARKS.getValue(it).width } ?: 2 * G.GHOST_R) * unit, unit, colors.ink)
        }

        // The first five, in the Home checklist's order as served, clockwise from the top.
        track(G.FIRST_FIVE_R, ExplorerRing.FIRST_FIVE in explorer.ringsDone)
        val five = explorer.firstFive
        five.forEachIndexed { i, item ->
            val p = at(G.FIRST_FIVE_R, G.firstFiveAngle(i, five.size))
            val step = item.step
            if (item.done && step != null) firstFiveMark(step, p, unit, ink, colors) else ghost(p, G.GHOST_R * unit, unit, colors)
            if (item.isNew) newMark(p, FIRST_FIVE_WIDTH * unit, unit, colors.ink)
        }

        // The centre: the first pebble, its own seal in its emotion's palette, or in ink.
        if (centre == null) {
            ghost(c, CENTRE_GHOST_R * unit, unit, colors)
        } else {
            val print =
                centrePalette?.let { centreSeals[SealKey(centre.valence, it)] }
                    ?: centreInk.of(sizeOf(centre.valence.intensity), centre.valence.polarity.inkPrint)
            sealAt(print, c, G.CENTRE_WIDTH * unit)
        }
    }
}

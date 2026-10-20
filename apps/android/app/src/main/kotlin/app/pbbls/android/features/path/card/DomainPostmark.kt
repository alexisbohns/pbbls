package app.pbbls.android.features.path.card

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import app.pbbls.android.core.designsystem.SpineTypography
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.ui.render.GlyphImage
import app.pbbls.android.features.path.card.logic.PostmarkNoise
import app.pbbls.android.features.path.card.logic.RingFits
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * One life domain as a round ink stamp (pebble card spec §Verso 4): the glyph in the
 * middle, the name ringing the edge per [RingFits], an outer and an inner ring, in
 * [ink]. Paper-coloured noise ([PostmarkNoise], tinted [paper], patterned by [seed])
 * is laid over the ink so it reads as pressed, not printed. Not clickable; the
 * ring is decorative and hidden from TalkBack (the verso's list reads the name).
 *
 * The outer ring and the glyph are the mock's at [CardLayout.POSTMARK_DIAMETER] and
 * scale with a shrunk [diameter]. The ring's type does not (it steps down through
 * [RingFits] instead, and ignores the font scale, being decoration inside a fixed
 * stamp), so its band, the baseline and the inner ring under it, is in absolute dp:
 * scaled with the stamp, the capitals' accents would run into the outer ring.
 */
@Composable
internal fun DomainPostmark(
    name: String,
    strokes: List<GlyphStroke>?,
    viewBox: String?,
    ink: Color,
    paper: Color,
    seed: Long,
    diameter: Dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val locale = LocalConfiguration.current.locales[0]
    val textMeasurer = rememberTextMeasurer()
    val scale = diameter / CardLayout.POSTMARK_DIAMETER
    val baselineRadiusPx = with(density) { (diameter / 2 - CardLayout.POSTMARK_TEXT_INSET).toPx() }
    val ring =
        remember(name, baselineRadiusPx, locale, density, textMeasurer) {
            RingLayout.of(name, baselineRadiusPx, locale, textMeasurer, Density(density.density, fontScale = 1f))
        }
    val noise = remember(seed) { noiseBitmap(seed) }

    Box(modifier.size(diameter).clearAndSetSemantics {}) {
        Canvas(Modifier.matchParentSize()) {
            val r = size.minDimension / 2
            drawCircle(
                color = ink,
                radius = r - (CardLayout.POSTMARK_OUTER_RING_INSET * scale).toPx(),
                style = Stroke(width = (CardLayout.POSTMARK_OUTER_RING * scale).toPx()),
            )
            drawCircle(
                color = ink,
                radius = baselineRadiusPx - CardLayout.POSTMARK_INNER_RING_DROP.toPx(),
                style = Stroke(width = (CardLayout.POSTMARK_INNER_RING * scale).toPx()),
            )
            ring.glyphs.forEach { glyph ->
                rotate(degrees = glyph.degrees, pivot = center) {
                    drawText(
                        textLayoutResult = glyph.layout,
                        color = ink,
                        topLeft = Offset(center.x - glyph.advance / 2, center.y - baselineRadiusPx - glyph.layout.firstBaseline),
                    )
                }
            }
        }
        if (strokes != null && viewBox != null) {
            GlyphImage(
                strokes = strokes,
                viewBox = viewBox,
                strokeColor = ink,
                modifier = Modifier.align(Alignment.Center).size(CardLayout.POSTMARK_GLYPH * scale),
            )
        }
        Canvas(Modifier.matchParentSize()) {
            val disc = Path().apply { addOval(Rect(center, size.minDimension / 2)) }
            clipPath(disc) {
                // Stretched, never tiled: the noise is not periodic, so a repeat would show its seams.
                drawImage(
                    image = noise,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    colorFilter = ColorFilter.tint(paper, BlendMode.SrcIn),
                )
            }
        }
    }
}

/** One ring character, laid out once: [degrees] clockwise from the top to its middle. */
private class RingGlyph(
    val layout: TextLayoutResult,
    val advance: Float,
    val degrees: Float,
)

private class RingLayout(
    val glyphs: List<RingGlyph>,
) {
    companion object {
        /**
         * Fits and places the ring (the mock's `ring.js`): every character advances by
         * its own measured width plus the fit's tracking, and the fit measures a string
         * as the sum of those same widths, so the ring closes exactly. The first
         * repetition's name is centred, upright, on the top.
         */
        fun of(
            name: String,
            baselineRadiusPx: Float,
            locale: Locale,
            textMeasurer: TextMeasurer,
            density: Density,
        ): RingLayout {
            val layouts = HashMap<Pair<Char, Float>, TextLayoutResult>()

            fun layout(
                c: Char,
                sizeSp: Float,
            ) = layouts.getOrPut(c to sizeSp) {
                textMeasurer.measure(
                    text = c.toString(),
                    style = SpineTypography.postmarkRing(sizeSp),
                    overflow = TextOverflow.Visible,
                    softWrap = false,
                    maxLines = 1,
                    density = density,
                )
            }

            // The float advance, not the intrinsic width, which rounds each character up to a whole pixel.
            fun charAdvance(
                c: Char,
                sizeSp: Float,
            ): Float = layout(c, sizeSp).getHorizontalPosition(1, usePrimaryDirection = true)

            val circumference = 2f * PI.toFloat() * baselineRadiusPx
            val fit = RingFits.fit(name, circumference, locale) { t, s -> t.sumOf { charAdvance(it, s).toDouble() }.toFloat() }
            val unitLength = fit.text.length / fit.repetitions
            val nameLength = unitLength - RingFits.SEPARATOR.length
            var nameSpan = 0f
            for (k in 0 until nameLength) {
                nameSpan += charAdvance(fit.text[k], fit.sizeSp) + if (k < nameLength - 1) fit.trackingPx else 0f
            }
            var arc = -nameSpan / 2
            val glyphs = ArrayList<RingGlyph>(fit.text.length)
            for (c in fit.text) {
                val advance = charAdvance(c, fit.sizeSp)
                if (!c.isWhitespace()) {
                    val mid = arc + advance / 2
                    glyphs += RingGlyph(layout(c, fit.sizeSp), advance, (mid / baselineRadiusPx * 180f / PI.toFloat()))
                }
                arc += advance + fit.trackingPx
            }
            return RingLayout(glyphs)
        }
    }
}

/** [PostmarkNoise] as white pixels carrying its alpha, for a tint to colour. */
private fun noiseBitmap(seed: Long): ImageBitmap {
    val size = CardLayout.NOISE_SIZE_PX
    val alphas = PostmarkNoise.alphas(size, seed)
    val pixels = IntArray(alphas.size) { (alphas[it] shl 24) or 0xFFFFFF }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}

package app.pbbls.android.features.path.card

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Which face the turn leads to. */
internal enum class CardFaceTarget { PEBBLE, MOMENT }

// The tiny card, from the Label exploration's 24 × 30 miniature: 15 × 22 dp, centred in the box.
private val MINI_CARD = Size(15f, 22f)
private const val MINI_CORNER = 2f
private const val MINI_OUTLINE = 1f
private const val MINI_RULE = 0.8f
private const val MINI_RULE_INSET = 2f
private val MINI_STONE = Size(9f, 7f)
private const val MINI_PHOTO_ALPHA = 0.35f
private const val MINI_LINE_ALPHA = 0.6f

/** The stone's centre, as a share of the card's height: a little above the middle. */
private const val MINI_STONE_CENTRE = 0.45f

/** The recto's photo: its margin inside the card, in dp, and the share of the card's height it ends at. */
private const val MINI_PHOTO_MARGIN = 2.3f
private const val MINI_PHOTO_SHARE = 0.4f

/** The recto's three legend lines, in dp from the card's top-left: where they start, and each one's (y, end x). */
private const val MINI_LINE_START = 3f
private val MINI_LINES = listOf(13f to 12f, 15.8f to 10.2f, 18.6f to 11.2f)

/**
 * A thumbnail of the face the turn leads to (from the Label exploration), tilted
 * [CardLayout.MINIATURE_TILT_DEG]: a card back with a stone in [stone] for
 * [CardFaceTarget.PEBBLE], or a recto with a photo block and three lines for
 * [CardFaceTarget.MOMENT]. Decorative: the turn button carries the label.
 */
@Composable
internal fun FaceMiniature(
    to: CardFaceTarget,
    stone: Color,
    ink: Color,
    stock: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier
            .size(CardLayout.MINIATURE_WIDTH, CardLayout.MINIATURE_HEIGHT)
            .graphicsLayer { rotationZ = CardLayout.MINIATURE_TILT_DEG },
    ) {
        val unit = 1.dp.toPx()
        val card = Size(MINI_CARD.width * unit, MINI_CARD.height * unit)
        val origin = Offset((size.width - card.width) / 2, (size.height - card.height) / 2)
        val corner = CornerRadius(MINI_CORNER * unit)
        drawRoundRect(stock, origin, card, corner)
        drawRoundRect(ink, origin, card, corner, style = Stroke(MINI_OUTLINE * unit))
        when (to) {
            CardFaceTarget.PEBBLE -> drawBack(origin, card, unit, stone, ink)
            CardFaceTarget.MOMENT -> drawRecto(origin, card, unit, ink)
        }
    }
}

/** The verso: an inner rule, and the stone a little above the middle. */
private fun DrawScope.drawBack(
    origin: Offset,
    card: Size,
    unit: Float,
    stone: Color,
    ink: Color,
) {
    val inset = MINI_RULE_INSET * unit
    drawRoundRect(
        color = ink,
        topLeft = origin + Offset(inset, inset),
        size = Size(card.width - inset * 2, card.height - inset * 2),
        cornerRadius = CornerRadius(MINI_CORNER * unit / 2),
        style = Stroke(MINI_RULE * unit),
    )
    val oval = Size(MINI_STONE.width * unit, MINI_STONE.height * unit)
    val centre = origin + Offset(card.width / 2, card.height * MINI_STONE_CENTRE)
    drawOval(stone, centre - Offset(oval.width / 2, oval.height / 2), oval)
}

/** The recto: the photo across the top 40 %, then three lines of legend. */
private fun DrawScope.drawRecto(
    origin: Offset,
    card: Size,
    unit: Float,
    ink: Color,
) {
    val margin = MINI_PHOTO_MARGIN * unit
    val photo = Size(card.width - margin * 2, card.height * MINI_PHOTO_SHARE - margin)
    drawRect(ink.copy(alpha = ink.alpha * MINI_PHOTO_ALPHA), origin + Offset(margin, margin), photo)
    val line = ink.copy(alpha = ink.alpha * MINI_LINE_ALPHA)
    val start = origin.x + MINI_LINE_START * unit
    MINI_LINES.forEach { (y, end) ->
        drawLine(
            color = line,
            start = Offset(start, origin.y + y * unit),
            end = Offset(origin.x + end * unit, origin.y + y * unit),
            strokeWidth = MINI_RULE * unit,
            cap = StrokeCap.Round,
        )
    }
}

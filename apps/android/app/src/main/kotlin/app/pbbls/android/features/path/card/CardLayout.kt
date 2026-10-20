package app.pbbls.android.features.path.card

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The held card's geometry, in dp from the final mocks (pebble card spec). */
internal object CardLayout {
    val MAX_WIDTH = 440.dp
    val PAGE_MARGIN = 16.dp

    /** Card units on the held card are not capped (the Spine caps them at 4.4 dp). */
    const val U_DIVISOR = 70f

    val FOOTER_PADDING_H = 16.dp
    val FOOTER_PADDING_V = 12.dp
    val FOOTER_GAP = 12.dp
    val TURN_TARGET = 48.dp
    val MINIATURE_WIDTH = 20.dp
    val MINIATURE_HEIGHT = 28.dp
    const val MINIATURE_TILT_DEG = -7f

    val VERSO_STONE_WIDTH = 150.dp
    val POSTMARK_DIAMETER = 72.dp
    val POSTMARK_GAP = 6.dp
    val POSTMARK_GLYPH = 38.dp
    val POSTMARK_OUTER_RING = 1.6.dp
    val POSTMARK_INNER_RING = 0.8.dp

    /**
     * Insets from the stamp's edge at [POSTMARK_DIAMETER], from the mock's 72-unit
     * SVG (outer ring r 34.6, text baseline r 26, inner ring r 23.6); a shrunk stamp
     * scales them with its diameter.
     */
    val POSTMARK_OUTER_RING_INSET = 1.4.dp
    val POSTMARK_TEXT_INSET = 10.dp
    val POSTMARK_INNER_RING_INSET = 12.4.dp
    val POSTMARK_ROW_TO_BAND = 18.dp
    val BAND_MIN_HEIGHT = 40.dp

    /**
     * The verso's frame, in card units (back-token3 `.back .panel`, `.vpanel`): the mat
     * sits where the recto's printed border does, the stock panel [VERSO_MAT_PADDING_UNITS]
     * inside it. The band's foot rests on the panel's foot line, the mat's padding showing under it.
     */
    const val VERSO_MAT_UNITS = 1.6f
    const val VERSO_MAT_PADDING_UNITS = 2.2f

    /** How far the band reaches up over the stock panel's foot (the mock's `margin-top: -20px`). */
    val BAND_OVER_PANEL = 20.dp

    /** From the face's foot to the band's. */
    fun bandFoot(u: Dp): Dp = u * (VERSO_MAT_UNITS + VERSO_MAT_PADDING_UNITS)

    const val NOISE_SIZE_PX = 128
}

package app.pbbls.android.features.path.card

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

    /** From the outer ring to the ring text's centre line. */
    val POSTMARK_TEXT_INSET = 9.dp
    val POSTMARK_ROW_TO_BAND = 18.dp
    val BAND_MIN_HEIGHT = 40.dp
    const val NOISE_SIZE_PX = 128
}

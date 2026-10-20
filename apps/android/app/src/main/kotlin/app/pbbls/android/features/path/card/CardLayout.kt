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

    /** The outer ring's inset from the stamp's edge at [POSTMARK_DIAMETER] (the mock's r 34.6 of 36); it scales with a shrunk stamp. */
    val POSTMARK_OUTER_RING_INSET = 1.4.dp

    /**
     * The ring text's band, in absolute dp because the ring type does not scale: the
     * baseline sits [POSTMARK_TEXT_INSET] in from the stamp's edge and the inner ring
     * [POSTMARK_INNER_RING_DROP] under it. 10.5 dp leaves room above the baseline for
     * Ysabeau's capital accents (É, Ê reach 0.907 em, 7.7 dp at 8.5 sp) inside the outer
     * ring even on a shrunk 61.5 dp stamp, where the inner ring (r 17.85) still clears
     * the glyph field (r 16.2).
     */
    val POSTMARK_TEXT_INSET = 10.5.dp
    val POSTMARK_INNER_RING_DROP = 2.4.dp
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

package app.pbbls.android.features.path.spine

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * The feed's phone geometry, from the web rule set (2026-10-11-web-path-spine-design.md,
 * §Page geometry) — one place, so the ground, the seams, the pebbles and the cards agree.
 */
internal object SpineLayout {
    /** The line's x. */
    val SPINE_X = 40.dp

    /** Where the content column starts. */
    val COL_X = 82.dp

    /** The column's right padding. */
    val PAD_R = 18.dp

    val DAY_PAD_TOP = 58.dp
    val DAY_PAD_BOTTOM = 34.dp

    /** Between two pebbles of one day. */
    val PEBBLE_GAP = 38.dp

    /** The stone's centre below the top of a card; an entry anchors at 0.6 × its name size. */
    val CARD_ANCHOR = 31.dp

    val SEAM_HEIGHT = 8.dp

    /** The day label in the margin: this wide, centred on the line, hung this far above the seam. */
    val LABEL_WIDTH = 46.dp
    val LABEL_TOP = (-14).dp

    /** The rake tile; the ground repeats it. */
    val RAKE_TILE = DpSize(36.dp, 240.dp)
}

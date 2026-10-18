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

    /** The seam proper: the hairline and its teeth. */
    val SEAM_HEIGHT = 8.dp

    /**
     * The day label hangs this far above the hairline, so `DaySeam` is `SEAM_LABEL_OVERHANG +
     * SEAM_HEIGHT` tall with its hairline this far down; a caller that wants the hairline at the
     * day's top takes this off the padding above the seam.
     */
    val SEAM_LABEL_OVERHANG = 14.dp

    /** The day label in the margin: this wide, centred on the line. */
    val LABEL_WIDTH = 46.dp

    /** The rake tile; the ground repeats it. */
    val RAKE_TILE = DpSize(36.dp, 240.dp)

    /** The verso's side padding: the masthead, the opening and the empty week sit in it. */
    val VERSO_PAD_X = 20.dp

    /** From the masthead to the kicker. */
    val OPENING_TOP = 46.dp

    /** From the kicker to the title. */
    val OPENING_TITLE_GAP = 16.dp

    /** From the title to the summary; the empty week's title to its line. */
    val OPENING_SUMMARY_GAP = 12.dp

    /** The line's last run into the mouth; the mouth's text starts under it. */
    val MOUTH_LINE = 26.dp

    /** From the mouth's label to the line that says when the next week begins. */
    val MOUTH_LINE_GAP = 4.dp

    /** The empty week's padding above and below. */
    val EMPTY_PAD_Y = 48.dp

    /** From the empty week's line to its button. */
    val EMPTY_BUTTON_GAP = 24.dp

    /** The empty week's button, inset from the page's sides. */
    val EMPTY_BUTTON_INSET = 48.dp
}

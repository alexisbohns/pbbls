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

    /**
     * A still day (web `min-h-[48px] pt-3.5`): *still* sits this far under the hairline and
     * the day runs at least this long, hairline to hairline.
     */
    val STILL_PAD_TOP = 14.dp
    val STILL_MIN_HEIGHT = 48.dp

    /** From the verso's last row to the first day's hairline: the web's aside `pb-2` + feed `mt-10`. */
    val FEED_TOP = 48.dp

    /** Under the mouth, so the last pebble clears the FAB: the web's feed `pb-24`. */
    val FEED_BOTTOM = 96.dp

    /** The verso's side padding: the masthead, the opening and the empty week sit in it. */
    val VERSO_PAD_X = 20.dp

    /** From the masthead to the kicker. */
    val OPENING_TOP = 46.dp

    /** From the kicker to the title. */
    val OPENING_TITLE_GAP = 16.dp

    /** From the title to the summary; the empty week's title to its line. */
    val OPENING_SUMMARY_GAP = 12.dp

    /*
     * The persons of the week (web `PersonsOfWeek.tsx`): the section's gap above and the label to
     * the first row. The row's grid and the day dots are `SpineLayoutTokens`.
     */
    val PERSONS_TOP = 40.dp
    val PERSONS_HEADER_GAP = 9.dp

    /** The fold row's indent: the web's `pl-10`. */
    val FOLD_INDENT = 40.dp

    /*
     * The contents (web `WeekContents.tsx`): the section's gap above, the label to the first
     * row, and the row's grid `40px 36px 1fr auto`, 42 px tall with 2 px above and below; the
     * name takes 6 px above and below and 10 px before the time.
     */
    val CONTENTS_TOP = 40.dp
    val CONTENTS_HEADER_GAP = 10.dp
    val CONTENTS_ROW_MIN = 42.dp
    val CONTENTS_DAY_COL = 40.dp
    val CONTENTS_SEAL_COL = 36.dp
    val CONTENTS_ROW_PAD_Y = 2.dp
    val CONTENTS_NAME_PAD_Y = 6.dp
    val CONTENTS_NAME_PAD_END = 10.dp

    /** The reading day's dot in the contents, and how far left of the row it sits. */
    val READING_DOT = 5.dp
    val READING_DOT_OFFSET = 12.dp

    /** The line's last run into the mouth; the mouth's text starts under it. */
    val MOUTH_LINE = 26.dp

    /** From the mouth's label to the line that says when the next week begins. */
    val MOUTH_LINE_GAP = 4.dp

    /** From the "begins" line to the next-week link's box (the web's `mt-2.5`). */
    val MOUTH_LINK_GAP = 10.dp

    /** The empty week's padding above and below. */
    val EMPTY_PAD_Y = 48.dp

    /** From the empty week's line to its button. */
    val EMPTY_BUTTON_GAP = 24.dp

    /** The empty week's button, inset from the page's sides. */
    val EMPTY_BUTTON_INSET = 48.dp
}

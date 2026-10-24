package app.pbbls.android.core.designsystem

import androidx.compose.ui.unit.dp

/**
 * The Spine's sizes shared beyond the feed: day dots, rules and the list row's grid
 * (`30px 1fr auto`, 10 px gaps, 44 px tall). The feed's own geometry stays in `SpineLayout`.
 */
object SpineLayoutTokens {
    /** A day dot; seven of them with their gaps are 79 dp. */
    val DOT = 7.dp
    val DOT_GAP = 5.dp

    /** An empty dot's inset ring. */
    val DOT_RING = 1.dp

    /** A section's rule above or below a row. */
    val RULE = 1.dp

    val ROW_MIN = 44.dp

    /** The row's leading column and the mark centred in it. */
    val MARK_COLUMN = 30.dp
    val MARK = 24.dp

    val ROW_GAP = 10.dp
}

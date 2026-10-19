package app.pbbls.android.features.path.spine

/**
 * A week the Spine's links can turn to (#1122): the masthead's way back and the mouth's
 * way on. Named by the ISO week number of the page it reaches, not of the calendar's
 * neighbour — the roll only holds weeks with moments plus the current one, so the two
 * differ whenever weeks are skipped, and a link must read as where it goes.
 *
 * @property number the ISO week number shown in the link ("week 35").
 * @property go what the link does: focus that week.
 */
data class WeekNeighbour(
    val number: Int,
    val go: () -> Unit,
)

package app.pbbls.android.features.path.spine.logic

/**
 * The persons list's fold (#1052): the server ranks the week's souls (#1207, `path_week_wrap`
 * `persons`, days present desc, then pebbles desc, then name); the list shows the first [CAP] and
 * folds the rest behind "and N others".
 */
object PersonsOfWeek {
    /** Rows shown before the rest fold into "and N others". */
    const val CAP = 8

    /** The first [cap] rows, and the rest behind "and N others". */
    fun <T> split(
        rows: List<T>,
        cap: Int = CAP,
    ): Pair<List<T>, List<T>> = rows.take(cap) to rows.drop(cap)
}

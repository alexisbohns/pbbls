package app.pbbls.android.features.path.strip

import java.time.LocalDate
import java.time.YearMonth

/** A page of the Path's pager (#1207, D2): one per focusable strip item, in strip order. */
sealed interface PathPage {
    data class Week(
        val weekStart: LocalDate,
    ) : PathPage

    data class Month(
        val month: YearMonth,
    ) : PathPage
}

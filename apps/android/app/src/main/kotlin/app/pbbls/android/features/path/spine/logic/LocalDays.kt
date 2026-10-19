package app.pbbls.android.features.path.spine.logic

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Port of `local-day.ts`: which calendar day of the week an instant falls on. */
object LocalDays {
    /**
     * Whole calendar days from [weekStart] to [at]'s local day in [zone]
     * (0 = Monday … 6 = Sunday for an ISO Monday; outside the week it is
     * negative or above 6). Local dates make the web's DST rounding
     * unnecessary: a 23- or 25-hour day is still one day apart.
     */
    fun dayIndexInWeek(
        weekStart: LocalDate,
        at: OffsetDateTime,
        zone: ZoneId,
    ): Int = ChronoUnit.DAYS.between(weekStart, at.atZoneSameInstant(zone).toLocalDate()).toInt()
}

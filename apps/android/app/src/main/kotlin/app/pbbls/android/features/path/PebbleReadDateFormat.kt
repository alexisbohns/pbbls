package app.pbbls.android.features.path

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Pure date formatting for the pebble detail title — mirrors iOS
 * `PebbleReadTitle.formattedDate`: weekday-abbrev, month-abbrev, day, year, then
 * a localized short time joined by a middot. Output is mixed-case; the read
 * title renders it in the `meta` token, which uppercases at draw time (the iOS
 * `.textCase(.uppercase)` analog). Sits next to [PathRowFormatting] and mirrors
 * its style so both stay JVM-testable.
 */
object PebbleReadDateFormat {
    fun format(
        happenedAt: OffsetDateTime,
        zone: ZoneId,
        locale: Locale,
    ): String {
        val local = happenedAt.atZoneSameInstant(zone)
        val date = local.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", locale))
        val time = local.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
        return "$date · $time"
    }
}

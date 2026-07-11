package app.pbbls.android.features.path

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Pure formatting for the pebble row's meta line — mirrors iOS
 * `PathPebbleRow.formattedWeekdayTime`: weekday + time only, because the
 * focused week is already known from the header, so day/month would be
 * redundant. The row composable appends the localized emotion name and
 * uppercases the whole line (meta token styling).
 */
object PathRowFormatting {
    fun weekdayTime(
        happenedAt: OffsetDateTime,
        zone: ZoneId,
        locale: Locale,
    ): String {
        val local = happenedAt.atZoneSameInstant(zone)
        val weekday = local.format(DateTimeFormatter.ofPattern("EEEE", locale))
        val time = local.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
        return "$weekday · $time"
    }
}

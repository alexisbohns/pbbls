package app.pbbls.android.features.path.create

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Pure date/time helpers for the [WhenRow] two-step picker (D15) — extracted so
 * the wall-clock ↔ UTC-millis conversions are JVM-testable (WhenDateTimeTest).
 * Material's `DatePicker` works in UTC-midnight millis; the pebble's
 * `happenedAt` is a zoned [OffsetDateTime]. These functions bridge the two
 * without ever letting a timezone offset shift the displayed calendar day.
 */
object WhenDateTime {
    /** DatePicker works in UTC-midnight millis; project the wall-clock date in [zone] to that. */
    fun toUtcDateMillis(
        happenedAt: OffsetDateTime,
        zone: ZoneId,
    ): Long {
        val date = happenedAt.atZoneSameInstant(zone).toLocalDate()
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    /** Combine a UTC-midnight date (from the picker) with a wall-clock time in [zone]. */
    fun combine(
        utcDateMillis: Long,
        hour: Int,
        minute: Int,
        zone: ZoneId,
    ): OffsetDateTime {
        val date = Instant.ofEpochMilli(utcDateMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return date.atTime(hour, minute).atZone(zone).toOffsetDateTime()
    }

    /** Row label: locale medium date · short time. */
    fun formatRow(
        happenedAt: OffsetDateTime,
        zone: ZoneId,
        locale: Locale,
    ): String {
        val local = happenedAt.atZoneSameInstant(zone)
        val date = local.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        val time = local.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
        return "$date · $time"
    }
}

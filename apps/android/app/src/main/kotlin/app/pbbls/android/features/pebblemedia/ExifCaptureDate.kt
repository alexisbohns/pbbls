package app.pbbls.android.features.pebblemedia

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

private const val TAG = "exif-capture-date"

/**
 * Reads the capture timestamp out of a picked image — ports iOS
 * `ExifCaptureDate.swift`.
 *
 * Must run *before* [ImagePipeline.process], which decodes through
 * `ImageDecoder` and re-encodes with `Bitmap.compress`: that writes no
 * metadata at all, so by the time bytes reach Storage the capture date is
 * gone. That is right for what we upload and useless for what we want to ask.
 *
 * Read from the picked `Uri` rather than `MediaStore.Images.DATE_TAKEN`: the
 * latter needs a media read permission the app does not request, to learn
 * something already present in bytes we are about to load anyway. The
 * photo-picker `Uri` grants read access to this one item and nothing else.
 */
object ExifCaptureDate {
    /**
     * EXIF `DateTimeOriginal` is `yyyy:MM:dd HH:mm:ss` with no zone — a wall
     * clock in whatever timezone the camera was in. We interpret it in the
     * device's current zone, which is right for the overwhelmingly common case
     * of recording a moment from a photo taken nearby.
     *
     * `Locale.ROOT` is mandatory here and is *not* the locale-pinning the
     * Android guidelines forbid: this parses a fixed machine format, not a
     * user-facing date, and a device locale carrying a non-Gregorian calendar
     * would otherwise misread it. (iOS pins `en_US_POSIX` for the same reason.)
     *
     * `STRICT` (which is why the year field is `uuuu`, not `yyyy`) is
     * load-bearing: the default `SMART` resolver *clamps* an out-of-range day to
     * the end of the month, so `0000:00:00 00:00:00` — the placeholder some
     * cameras write — would resolve to a real date and silently become the
     * pebble's moment.
     */
    private val FORMATTER: DateTimeFormatter =
        DateTimeFormatter
            .ofPattern("uuuu:MM:dd HH:mm:ss", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT)

    /**
     * The photo's capture date, or null when the stream is unreadable, is not an
     * image, or carries no parseable EXIF date. Every null path means the caller
     * falls back to now — never to a date we guessed.
     */
    fun from(
        context: Context,
        uri: Uri,
        zone: ZoneId = ZoneId.systemDefault(),
    ): OffsetDateTime? =
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                parse(ExifInterface(stream).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL), zone)
            }
        } catch (e: Exception) {
            // A pick with no readable EXIF is ordinary, not a failure worth
            // surfacing — the date step just opens on now.
            Log.i(TAG, "no readable EXIF capture date on the picked image", e)
            null
        }

    /**
     * Pure parse of a raw `DateTimeOriginal` value. Split from [from] so the
     * format handling is JVM-unit-testable without a `Context`, an image
     * fixture, or the Android framework.
     *
     * Null for absent, blank, and malformed values — including the all-zero
     * placeholder (`0000:00:00 00:00:00`) some cameras write, which parses as a
     * date only under a lenient resolver.
     */
    fun parse(
        raw: String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): OffsetDateTime? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return try {
            LocalDateTime.parse(trimmed, FORMATTER).atZone(zone).toOffsetDateTime()
        } catch (e: java.time.format.DateTimeParseException) {
            null
        }
    }
}

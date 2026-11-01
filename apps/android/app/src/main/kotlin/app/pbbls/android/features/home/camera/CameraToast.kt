package app.pbbls.android.features.home.camera

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The snackbar's time (#1383 D7, iOS `MomentToast`): the shutter in the device's zone, in [locale]'s
 * short time form ("14:32", "2:32 PM").
 */
fun cameraToastTime(
    shutter: OffsetDateTime,
    zone: ZoneId,
    locale: Locale,
): String = shutter.atZoneSameInstant(zone).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

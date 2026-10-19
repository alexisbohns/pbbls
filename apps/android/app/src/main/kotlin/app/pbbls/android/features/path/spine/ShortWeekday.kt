package app.pbbls.android.features.path.spine

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** "mon", "lun": the locale's short weekday, lowercase, without the abbreviation point. */
internal fun shortWeekday(
    dayOfWeek: DayOfWeek,
    locale: Locale,
): String =
    dayOfWeek
        .getDisplayName(TextStyle.SHORT, locale)
        .lowercase(locale)
        .trimEnd('.')

package app.pbbls.android.features.path.spine.logic

import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.PathSoul
import java.text.Collator
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** One soul of the week and the days they appear on. */
data class PersonOfWeek(
    val soul: PathSoul,
    /** Monday first, by the local day of each pebble's `happenedAt`. */
    val days: BooleanArray,
    val dayCount: Int,
    val pebbleCount: Int,
) {
    // Hand-written: a data class would compare `days` by array identity.
    override fun equals(other: Any?): Boolean =
        other is PersonOfWeek &&
            soul == other.soul &&
            days.contentEquals(other.days) &&
            dayCount == other.dayCount &&
            pebbleCount == other.pebbleCount

    override fun hashCode(): Int {
        var result = soul.hashCode()
        result = 31 * result + days.contentHashCode()
        result = 31 * result + dayCount
        result = 31 * result + pebbleCount
        return result
    }

    override fun toString(): String = "PersonOfWeek(soul=$soul, days=${days.toList()}, dayCount=$dayCount, pebbleCount=$pebbleCount)"
}

/**
 * Port of `persons-of-week.ts` (#1052, spec §Persons of the week): every soul
 * that appears in the week, with the days they appear on.
 *
 * Only a pebble names people: a bare moment (#1050) brings no one.
 *
 * Order — days present desc, then pebbles desc, then name by locale collation —
 * is the one rule every surface uses, so the most present people come first
 * here, in each pebble's people line, and in the same order on iOS and web.
 */
object PersonsOfWeek {
    /** Rows shown before the rest fold into "and N others". */
    const val CAP = 8

    private const val DAYS_IN_WEEK = 7

    private class Row(
        val soul: PathSoul,
    ) {
        val days = BooleanArray(DAYS_IN_WEEK)
        var dayCount = 0
        var pebbleCount = 0
    }

    fun of(
        moments: List<PathMoment>,
        weekStart: LocalDate,
        zone: ZoneId,
        locale: Locale,
    ): List<PersonOfWeek> {
        val rows = LinkedHashMap<String, Row>()
        for (moment in moments) {
            val day = LocalDays.dayIndexInWeek(weekStart, moment.happenedAt, zone)
            if (day !in 0 until DAYS_IN_WEEK) continue
            // A pebble can list a soul twice only through a data bug; count it once.
            for (soul in moment.pebble
                ?.souls
                .orEmpty()
                .distinctBy { it.id }) {
                val row = rows.getOrPut(soul.id) { Row(soul) }
                if (!row.days[day]) {
                    row.days[day] = true
                    row.dayCount += 1
                }
                row.pebbleCount += 1
            }
        }

        val collator = Collator.getInstance(locale)
        return rows.values
            .map { PersonOfWeek(it.soul, it.days, it.dayCount, it.pebbleCount) }
            .sortedWith(
                compareByDescending<PersonOfWeek> { it.dayCount }
                    .thenByDescending { it.pebbleCount }
                    .thenComparator { a, b -> collator.compare(a.soul.name, b.soul.name) }
                    // Two souls can share a name; their ids keep the order stable anyway.
                    .thenBy { it.soul.id },
            )
    }

    /** The first [cap] rows, and the rest behind "and N others". */
    fun split(
        rows: List<PersonOfWeek>,
        cap: Int = CAP,
    ): Pair<List<PersonOfWeek>, List<PersonOfWeek>> = rows.take(cap) to rows.drop(cap)
}

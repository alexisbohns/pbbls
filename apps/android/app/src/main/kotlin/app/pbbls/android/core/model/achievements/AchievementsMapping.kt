package app.pbbls.android.core.model.achievements

import app.pbbls.android.core.model.Valence
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * The body as the app reads it: decode, then map (#1307). Throws only on a body that is not the
 * payload's JSON shape (`SerializationException` or `IllegalArgumentException`).
 */
fun decodeAchievementsPage(body: String): AchievementsPage = AchievementsJson.decodeFromString<AchievementsPageDto>(body).toModel()

fun AchievementsPageDto.toModel(): AchievementsPage =
    AchievementsPage(
        since = since?.let(::parseDay),
        daysKept = daysKept,
        explorer = explorer?.toModel(),
        emotions = emotions?.toModel(),
        domains = domains?.toModel(),
        pebbles = pebbles?.toModel(),
        glyphsCarved = glyphsCarved?.toModel(),
        glyphSales = glyphSales?.toModel(),
        years = years.mapNotNull { it.toModel() },
        seen =
            seen.entries
                .mapNotNull { (card, at) ->
                    val key = AchievementCard.of(card) ?: return@mapNotNull null
                    at?.let(::parseInstant)?.let { key to it }
                }.toMap(),
        shelf = shelf.mapNotNull { it.toModel() },
        shelfCount = shelfCount,
    )

private val SHORT_OFFSET = Regex("""([+-]\d{2})$""")

/**
 * A timestamp crossing the boundary, read tolerantly (standing rule): `Z` or `+00:00`, any
 * fraction, and Postgres's own `2026-03-02 09:00:00+00` spelling. Null when unreadable.
 */
internal fun parseInstant(raw: String): Instant? {
    val iso = raw.trim().replace(' ', 'T').replace(SHORT_OFFSET, "$1:00")
    return try {
        OffsetDateTime.parse(iso).toInstant()
    } catch (e: DateTimeParseException) {
        null
    }
}

/** A `YYYY-MM-DD` local day (D17). Null when unreadable. */
internal fun parseDay(raw: String): LocalDate? =
    try {
        LocalDate.parse(raw.trim())
    } catch (e: DateTimeParseException) {
        null
    }

/** A snapshot without a moment to open or a time to date it cannot be drawn: dropped, its row kept. */
private fun PebbleSnapshotDto.toModel(): PebbleSnapshot? {
    val id = momentId ?: return null
    val at = happenedAt?.let(::parseInstant) ?: return null
    return PebbleSnapshot(
        momentId = id,
        name = name,
        happenedAt = at,
        emotionId = emotionId,
        // Out of range is decode drift, drawn as the Path draws it (NEUTRAL_MEDIUM).
        valence = Valence.fromOrDefault(positiveness ?: 0, intensity ?: 2),
    )
}

private fun CompletionDto.toModel(): Completion? {
    val completed = completedAt?.let(::parseInstant) ?: return null
    val offered = offeredFrom?.let(::parseInstant) ?: return null
    return Completion(completed, offered, ready, pulledAt?.let(::parseInstant), pebble?.toModel())
}

private fun ExplorerDto.toModel(): ExplorerSection =
    ExplorerSection(
        firstPebble = firstPebble?.toModel(),
        firstFive =
            firstFive.mapNotNull { item ->
                FirstStep.of(item.slug)?.let { step ->
                    FirstFiveItem(
                        step = step,
                        done = item.done,
                        on = item.on?.let(::parseDay),
                        subject =
                            item.subject?.let { s ->
                                val kind = SubjectKind.of(s.kind)
                                val id = s.id
                                if (kind == null || id == null) null else FirstSubject(kind, id, s.name)
                            },
                        isNew = item.isNew,
                    )
                }
            },
        sixFirsts =
            sixFirsts.mapNotNull { item ->
                StoneKind.of(item.kind)?.let {
                    SixFirst(it, item.done, item.on?.let(::parseDay), item.pebble?.toModel(), item.isNew)
                }
            },
        twentyFive =
            twentyFive?.let {
                TwentyFive(it.count, it.done, it.on?.let(::parseDay), it.pebble?.toModel(), it.isNew)
            },
        ringsDone = ringsDone.filterNotNull().mapNotNull(ExplorerRing::of).toSet(),
        completion = completion?.toModel(),
    )

private fun EmotionsDto.toModel(): EmotionsSection {
    // The seat index is the rosace's position, not an order the client chooses: the seats are
    // laid out by it, and a seat the client cannot place (no emotion id) is dropped.
    val mapped =
        seats
            .mapNotNull { s ->
                s.emotionId?.let {
                    EmotionSeat(s.seat, it, s.family, s.met, s.on?.let(::parseDay), s.pebble?.toModel(), s.isNew)
                }
            }.sortedBy { it.seat }
    val bySeat = mapped.associateBy { it.seat }
    return EmotionsSection(
        count = count,
        of = of,
        seats = mapped,
        // The sheet's order is the server's: families as sent, each list as sent.
        families =
            families.mapNotNull { f ->
                f.family?.let { family ->
                    EmotionFamily(
                        family,
                        f.met.filterNotNull().mapNotNull(bySeat::get),
                        f.notYet.filterNotNull().mapNotNull(bySeat::get),
                    )
                }
            },
        since = since?.let(::parseDay),
        completion = completion?.toModel(),
    )
}

private fun DomainsDto.toModel(): DomainsSection =
    DomainsSection(
        count = count,
        of = of,
        coverage = Coverage.of(coverage),
        entered =
            entered.mapNotNull { e ->
                e.domainId?.let { DomainEntry(it, e.on?.let(::parseDay), e.pebble?.toModel(), e.isNew) }
            },
        notYet = notYet.filterNotNull(),
        completion = completion?.toModel(),
    )

private fun CountingDto.toModel(): CountingSection =
    CountingSection(
        count = count,
        levels =
            levels.map { l ->
                StackLevel(l.threshold, l.on?.let(::parseDay), l.pebble?.toModel(), l.isNew)
            },
        next = next?.let { NextLevel(it.threshold, it.left, it.progress.toFloat().coerceIn(0f, 1f)) },
        ladder = ladder.filterNotNull(),
        thenEvery = thenEvery,
        since = since?.let(::parseDay),
    )

/** Twelve distinct months or nothing: the disc's geometry has twelve spokes. */
private fun YearDto.toModel(): AchievementYear? {
    val byMonth =
        months
            .filter { it.month in 1..12 }
            .associateBy { it.month }
    if (byMonth.size != 12) return null
    return AchievementYear(
        year = year,
        current = current,
        daysTarget = daysTarget,
        months =
            (1..12).map { m ->
                val d = byMonth.getValue(m)
                AchievementMonth(
                    month = m,
                    state = MonthState.of(d.state),
                    family = d.family,
                    momentDays = d.momentDays,
                    pebbleDays = d.pebbleDays,
                    fullWeeks = d.fullWeeks,
                    weeksFilled = d.weeksFilled,
                    level = d.level.coerceIn(0, 2),
                    progress = d.progress.toFloat().coerceIn(0f, 1f),
                    done = MonthDone(d.done.moments, d.done.pebbles, d.done.weeks),
                )
            },
        listed = listed.filterNotNull().filter { it in 1..12 },
        nextStep =
            nextStep?.let { n ->
                Needs.of(n.needs)?.let { NextStep(n.month, it, n.daysLeft, n.reachable) }
            },
    )
}

private fun ShelfRowDto.toModel(): ShelfItem? {
    val s = slug ?: return null
    val f = family ?: return null
    return ShelfItem(s, f, threshold, unlockedOn?.let(::parseDay))
}

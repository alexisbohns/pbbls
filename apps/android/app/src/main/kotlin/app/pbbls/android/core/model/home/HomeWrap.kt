package app.pbbls.android.core.model.home

import app.pbbls.android.core.model.achievements.parseInstant
import app.pbbls.android.core.model.wrap.CoverDay
import app.pbbls.android.core.model.wrap.CoverDto
import app.pbbls.android.core.model.wrap.CoverPebble
import app.pbbls.android.core.model.wrap.MonthCover
import app.pbbls.android.core.model.wrap.WrapJson
import app.pbbls.android.core.model.wrap.polarityOf
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * One month on Home (#1383, spec D12; iOS `HomeMonth`): its pebble count, its garland cover, and
 * whether the reader has unfolded it yet. [revealedAt] is the server's record of the unfold, null
 * while the month is folded.
 */
data class HomeWrapMonth(
    val month: YearMonth,
    val count: Int,
    val revealedAt: Instant?,
    val cover: MonthCover?,
) {
    val isFolded: Boolean get() = revealedAt == null
}

/**
 * `home_wraps(p_tz)`, as the server sends it: `{"months": [...]}`. Each month decodes on its own,
 * so the list is not a DTO field (see [decodeHomeWraps]).
 */
@Serializable
data class HomeWrapMonthDto(
    val month: String,
    val count: Int = 0,
    @SerialName("revealed_at")
    val revealedAt: String? = null,
    val cover: CoverDto? = null,
)

/** `reveal_wrap(p_month, p_tz)`: the month and when the server recorded its unfold. */
@Serializable
data class HomeRevealDto(
    val month: String = "",
    @SerialName("revealed_at")
    val revealedAt: String? = null,
)

/**
 * The months of a `home_wraps` body, newest first (iOS `HomeWrapsPayload`). A month this build
 * cannot read (an unreadable date, a malformed cover) is dropped and reported to [onDrop], never
 * fatal: one bad month must not hide Home's wraps. A missing `months` reads as none. A body that is
 * not a JSON object still throws, so a whole-response failure reaches the caller.
 */
fun decodeHomeWraps(
    body: String,
    onDrop: (index: Int, cause: Exception) -> Unit,
): List<HomeWrapMonth> {
    val root =
        WrapJson.parseToJsonElement(body) as? JsonObject
            ?: throw SerializationException("expected a home_wraps object")
    val items = (root["months"] as? JsonArray).orEmpty()
    return items
        .mapIndexedNotNull { index, element -> decodeMonth(element, index, onDrop) }
        .sortedByDescending { it.month }
}

/** When a `reveal_wrap` body says the month was unfolded; null when the server recorded nothing. */
fun decodeRevealedAt(body: String): Instant? = WrapJson.decodeFromString<HomeRevealDto>(body).revealedAt?.let(::parseInstant)

private fun decodeMonth(
    element: JsonElement,
    index: Int,
    onDrop: (index: Int, cause: Exception) -> Unit,
): HomeWrapMonth? =
    try {
        if (element is JsonNull) throw SerializationException("null month")
        WrapJson.decodeFromJsonElement(HomeWrapMonthDto.serializer(), element).toModel()
    } catch (e: IllegalArgumentException) {
        // SerializationException included: it is an IllegalArgumentException.
        onDrop(index, e)
        null
    } catch (e: DateTimeException) {
        onDrop(index, e)
        null
    }

/** Throws [DateTimeException] on an unreadable month, which drops it. */
fun HomeWrapMonthDto.toModel(): HomeWrapMonth =
    HomeWrapMonth(
        month = YearMonth.from(LocalDate.parse(month)),
        count = count,
        // Tolerant: any precision, `Z` or an offset; an unparseable stamp reads as folded.
        revealedAt = revealedAt?.let(::parseInstant),
        cover = cover?.toCover(),
    )

private fun CoverDto.toCover(): MonthCover =
    MonthCover(
        days =
            days.map { d ->
                CoverDay(d.day, d.pebbles.map { CoverPebble(it.momentId, it.emotionId, it.intensity, polarityOf(it.polarity)) })
            },
        soulsInOrder = soulsInOrder,
        inks = inks,
        papers = papers,
    )

package app.pbbls.android.core.model.home

import app.pbbls.android.core.model.UuidStringSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import java.util.Locale

/**
 * One About Pebbles reading (#1383, spec D13; iOS `Guide`): a `guides` row, published, in
 * [sortOrder]. EN is required; each FR field falls back to its EN field on its own, like `Log`.
 */
data class Guide(
    val id: String,
    val slug: String,
    val sortOrder: Int,
    val illustration: GuideIllustration?,
    val titleEn: String,
    val titleFr: String?,
    val summaryEn: String,
    val summaryFr: String?,
    val bodyEn: String,
    val bodyFr: String?,
) {
    /** The reading in [locale]'s language: French when it prefers French, field by field over EN. */
    fun localized(locale: Locale): LocalizedGuide =
        if (locale.language == "fr") {
            LocalizedGuide(titleFr ?: titleEn, summaryFr ?: summaryEn, bodyFr ?: bodyEn)
        } else {
            LocalizedGuide(titleEn, summaryEn, bodyEn)
        }
}

/** A [Guide]'s words in one language. [body] is markdown. */
data class LocalizedGuide(
    val title: String,
    val summary: String,
    val body: String,
)

/**
 * The nine illustrations the clients draw from their own parts; mirrors the column's CHECK. A value
 * this build does not know reads as null (the row is kept, drawn with the default chip).
 */
enum class GuideIllustration(
    val wire: String,
) {
    PEBBLES("pebbles"),
    STONES("stones"),
    EMOTIONS("emotions"),
    VALENCE("valence"),
    DOMAINS("domains"),
    COLLECTIONS("collections"),
    SOULS("souls"),
    GLYPHS("glyphs"),
    KARMA("karma"),
    ;

    companion object {
        fun fromWire(wire: String?): GuideIllustration? = entries.firstOrNull { it.wire == wire }
    }
}

/** A `guides` row as PostgREST sends it. Strict like iOS: a missing or null EN field fails the row. */
@Serializable
data class GuideDto(
    @Serializable(with = UuidStringSerializer::class)
    val id: String,
    val slug: String,
    @SerialName("sort_order")
    val sortOrder: Int,
    val illustration: String? = null,
    @SerialName("title_en")
    val titleEn: String,
    @SerialName("title_fr")
    val titleFr: String? = null,
    @SerialName("summary_en")
    val summaryEn: String,
    @SerialName("summary_fr")
    val summaryFr: String? = null,
    @SerialName("body_md_en")
    val bodyMdEn: String,
    @SerialName("body_md_fr")
    val bodyMdFr: String? = null,
)

fun GuideDto.toModel(): Guide =
    Guide(
        id = id,
        slug = slug,
        sortOrder = sortOrder,
        illustration = GuideIllustration.fromWire(illustration),
        titleEn = titleEn,
        titleFr = titleFr,
        summaryEn = summaryEn,
        summaryFr = summaryFr,
        bodyEn = bodyMdEn,
        bodyFr = bodyMdFr,
    )

/** The guides' decoder: extra columns (`published`, the timestamps) are ignored, nothing is coerced. */
val GuideJson: Json = Json { ignoreUnknownKeys = true }

/**
 * The rows of a `guides` read, in the order sent (iOS `LossyGuideArray`). One bad row must not hide
 * the About section: it is dropped and reported to [onSkip]. A body that is not an array still
 * throws, so a whole-response failure reaches the caller.
 */
fun decodeGuides(
    body: String,
    onSkip: (index: Int, cause: Exception) -> Unit,
): List<Guide> {
    val rows =
        GuideJson.parseToJsonElement(body) as? JsonArray
            ?: throw SerializationException("expected a top-level JSON array of guide rows")
    return rows.mapIndexedNotNull { index, element ->
        try {
            GuideJson.decodeFromJsonElement(GuideDto.serializer(), element).toModel()
        } catch (e: IllegalArgumentException) {
            // SerializationException included: it is an IllegalArgumentException. So is a malformed id.
            onSkip(index, e)
            null
        }
    }
}

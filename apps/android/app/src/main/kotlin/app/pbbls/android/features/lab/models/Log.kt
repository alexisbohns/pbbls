package app.pbbls.android.features.lab.models

import app.pbbls.android.features.path.models.OffsetDateTimeSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.OffsetDateTime
import java.util.Locale
import java.util.UUID
import kotlin.math.max

/** `logs.species` — the two published kinds. */
@Serializable
enum class LogSpecies {
    @SerialName("announcement")
    ANNOUNCEMENT,

    @SerialName("feature")
    FEATURE,
}

/**
 * `logs.platform`. The DB check constraint also admits `project` and `infra`,
 * which are deliberately NOT listed here: rows carrying them fail to decode
 * and are dropped by the lossy feed decode — matching iOS's strict enum
 * (M44 design D1). If those rows should ever surface in-app, both mobile
 * surfaces change together. Decoded but unused by any UI (iOS parity).
 */
@Serializable
enum class LogPlatform {
    @SerialName("webapp")
    WEBAPP,

    @SerialName("ios")
    IOS,

    @SerialName("android")
    ANDROID,

    @SerialName("all")
    ALL,
}

/** `logs.status` — `planned` exists in the schema but no feed queries it. */
@Serializable
enum class LogStatus {
    @SerialName("backlog")
    BACKLOG,

    @SerialName("planned")
    PLANNED,

    @SerialName("in_progress")
    IN_PROGRESS,

    @SerialName("shipped")
    SHIPPED,
}

/**
 * Decodes a UUID column as its String form but VALIDATES it — a malformed id
 * must fail the row so the lossy decode drops it, mirroring iOS's strict
 * `UUID` field (the "not-a-uuid" rows in the ported suite).
 */
object UuidStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.pbbls.android.features.lab.models.UuidString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val raw = decoder.decodeString()
        UUID.fromString(raw)
        return raw
    }

    override fun serialize(
        encoder: Encoder,
        value: String,
    ) {
        encoder.encodeString(value)
    }
}

/**
 * One Lab entry — mirrors `public.logs` read through `v_logs_with_counts`
 * (which adds `reaction_count`). Ports iOS `Log`. Every nullable field
 * defaults to null so rows may omit the key entirely (PostgREST does both).
 */
@Serializable
data class Log(
    @Serializable(with = UuidStringSerializer::class)
    val id: String,
    val species: LogSpecies,
    val platform: LogPlatform,
    val status: LogStatus,
    @SerialName("title_en")
    val titleEn: String,
    @SerialName("title_fr")
    val titleFr: String? = null,
    @SerialName("summary_en")
    val summaryEn: String,
    @SerialName("summary_fr")
    val summaryFr: String? = null,
    @SerialName("body_md_en")
    val bodyMdEn: String? = null,
    @SerialName("body_md_fr")
    val bodyMdFr: String? = null,
    @SerialName("cover_image_path")
    val coverImagePath: String? = null,
    @SerialName("external_url")
    val externalUrl: String? = null,
    val published: Boolean,
    @SerialName("published_at")
    @Serializable(with = OffsetDateTimeSerializer::class)
    val publishedAt: OffsetDateTime? = null,
    @SerialName("released_at")
    @Serializable(with = OffsetDateTimeSerializer::class)
    val releasedAt: OffsetDateTime? = null,
    @SerialName("created_at")
    @Serializable(with = OffsetDateTimeSerializer::class)
    val createdAt: OffsetDateTime,
    @SerialName("reaction_count")
    val reactionCount: Int,
) {
    /** Localized title — fr prefers `title_fr` falling back to `title_en`. */
    fun title(locale: Locale): String = if (prefersFrench(locale)) titleFr ?: titleEn else titleEn

    /** Localized summary — same fallback shape as [title]. */
    fun summary(locale: Locale): String = if (prefersFrench(locale)) summaryFr ?: summaryEn else summaryEn

    /** Localized markdown body, nullable — same fallback shape as [title]. */
    fun body(locale: Locale): String? = if (prefersFrench(locale)) bodyMdFr ?: bodyMdEn else bodyMdEn

    /**
     * Optimistic-count copy, clamped at zero (iOS `withAdjustedCount`) — list
     * diffing stays stable because every other field is identical.
     */
    fun withAdjustedCount(delta: Int): Log = copy(reactionCount = max(0, reactionCount + delta))

    private fun prefersFrench(locale: Locale): Boolean = locale.language == "fr"
}

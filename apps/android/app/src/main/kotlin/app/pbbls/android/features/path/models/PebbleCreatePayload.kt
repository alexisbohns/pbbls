package app.pbbls.android.features.path.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

/**
 * Encodable payload sent as the `payload` jsonb parameter of the `create_pebble`
 * RPC (via the `compose-pebble` edge function) — mirrors iOS
 * `PebbleCreatePayload.swift`.
 *
 * Declares **no default values** (D3) — except [snaps]: the write service's
 * `Json` sets `explicitNulls = true` (so [description] / [glyphId] encode as
 * literal JSON `null` rather than being omitted) but leaves `encodeDefaults`
 * false, so [snaps]' null default makes the key **absent** on photo-less
 * creates and present only when an uploaded snap exists (M42 design D5).
 * [visibility] serializes to `"private"` via the [Visibility] enum's
 * `@SerialName`; [happenedAt] encodes as an ISO-8601 string via
 * [OffsetDateTimeSerializer].
 */
@Serializable
data class PebbleCreatePayload(
    val name: String,
    val description: String?,
    @SerialName("happened_at")
    @Serializable(with = OffsetDateTimeSerializer::class)
    val happenedAt: OffsetDateTime,
    val intensity: Int,
    val positiveness: Int,
    val visibility: Visibility,
    @SerialName("emotion_id")
    val emotionId: String,
    @SerialName("domain_ids")
    val domainIds: List<String>,
    @SerialName("soul_ids")
    val soulIds: List<String>,
    @SerialName("collection_ids")
    val collectionIds: List<String>,
    @SerialName("glyph_id")
    val glyphId: String?,
    val snaps: List<PebbleSnapPayload>? = null,
) {
    companion object {
        fun from(
            draft: PebbleDraft,
            snaps: List<PebbleSnapPayload>? = null,
        ): PebbleCreatePayload {
            require(draft.isValid) { "PebbleCreatePayload.from called with an invalid draft" }
            val valence = draft.valence!!
            val trimmedDescription = draft.description.trim()
            return PebbleCreatePayload(
                name = draft.name.trim(),
                description = trimmedDescription.ifEmpty { null },
                happenedAt = draft.happenedAt,
                intensity = valence.intensity,
                positiveness = valence.positiveness,
                visibility = draft.visibility,
                emotionId = draft.emotionId!!,
                domainIds = listOf(draft.domainId!!),
                soulIds = draft.soulIds,
                collectionIds = draft.collectionId?.let { listOf(it) } ?: emptyList(),
                glyphId = draft.glyphId,
                snaps = snaps,
            )
        }
    }
}

package app.pbbls.android.features.path.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

/**
 * One snap link in an update payload — `{id, storage_path, sort_order}`.
 * Mirrors the nested `SnapPayload` in iOS `PebbleUpdatePayload.swift`.
 */
@Serializable
data class PebbleSnapPayload(
    val id: String,
    @SerialName("storage_path")
    val storagePath: String,
    @SerialName("sort_order")
    val sortOrder: Int,
)

/**
 * Payload for the `update_pebble` RPC (via compose-pebble-update). We always send
 * every scalar and every array — `update_pebble` (20260426000002) uses
 * `coalesce(...)` for scalars and `case when payload ? 'key'` for `description` /
 * `glyph_id` / the join arrays, so an omitted key means "keep". Sending everything
 * (with explicit nulls) is proven on iOS + web and lets edit clear description and
 * glyph. `snaps` is round-tripped from the loaded detail (risk 5).
 */
@Serializable
data class PebbleUpdatePayload(
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
    val snaps: List<PebbleSnapPayload>,
) {
    companion object {
        fun from(
            draft: PebbleDraft,
            snaps: List<PebbleSnapPayload>,
        ): PebbleUpdatePayload {
            require(draft.isValid) { "PebbleUpdatePayload.from called with an invalid draft" }
            val valence = draft.valence!!
            val trimmedDescription = draft.description.trim()
            return PebbleUpdatePayload(
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

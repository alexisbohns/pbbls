package app.pbbls.android.features.path.models

import java.time.OffsetDateTime

/**
 * In-progress form state for the create/edit pebble surfaces — mirrors iOS
 * `PebbleDraft.swift`. An immutable value held in `remember { mutableStateOf }`
 * at the screen shell and mutated by [copy] (D4). Nullable fields mean "not yet
 * picked"; non-nullables carry sensible defaults. NOT `@Serializable` — the
 * wire payloads ([PebbleCreatePayload] / [PebbleUpdatePayload]) are built from
 * it via their `from` factories.
 */
data class PebbleDraft(
    val happenedAt: OffsetDateTime = OffsetDateTime.now(),
    val name: String = "",
    val description: String = "",
    val emotionId: String? = null,
    val domainId: String? = null,
    val valence: Valence? = null,
    val soulIds: List<String> = emptyList(),
    val collectionId: String? = null,
    val glyphId: String? = null,
    val visibility: Visibility = Visibility.PRIVATE,
) {
    val isValid: Boolean
        get() = name.trim().isNotEmpty() && emotionId != null && domainId != null && valence != null

    companion object {
        fun from(detail: PebbleDetail): PebbleDraft =
            PebbleDraft(
                happenedAt = detail.happenedAt,
                name = detail.name,
                description = detail.description ?: "",
                emotionId = detail.emotion.id,
                domainId = detail.domains.firstOrNull()?.id,
                valence = detail.valence,
                soulIds = detail.souls.map { it.id },
                collectionId = detail.collections.firstOrNull()?.id,
                glyphId = detail.glyphId,
                visibility = detail.visibility,
            )
    }
}

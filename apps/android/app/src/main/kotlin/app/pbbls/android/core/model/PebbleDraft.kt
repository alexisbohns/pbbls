package app.pbbls.android.core.model

import java.time.OffsetDateTime

/**
 * In-progress form state for the create/edit pebble surfaces — mirrors iOS
 * `PebbleDraft.swift`. An immutable value held in `remember { mutableStateOf }`
 * at the screen shell and mutated by [copy] (D4). Nullable fields mean "not yet
 * picked"; non-nullables carry sensible defaults. NOT `@Serializable` — the
 * wire payloads ([PebbleCreatePayload], [MomentSavePayload],
 * [PebbleCompletePayload]) are built from it.
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
    // Owner-only unless chosen (M51): 'private' now means connections-visible.
    val visibility: Visibility = Visibility.SECRET,
) {
    val isValid: Boolean
        get() = name.trim().isNotEmpty() && emotionId != null && domainId != null && valence != null

    /** The pebble half, for `complete_pebble`. */
    val answers: PebbleAnswers
        get() = PebbleAnswers(valence, emotionId, domainId, soulIds, collectionId, visibility)

    companion object {
        /**
         * Prefill for Edit moment and Edit pebble. A pebble with several
         * domains or collections keeps the first of each, as before the split.
         */
        fun from(moment: MomentDetail): PebbleDraft {
            val pebble = moment.pebble
            return PebbleDraft(
                happenedAt = moment.happenedAt,
                name = moment.name.orEmpty(),
                description = moment.description.orEmpty(),
                emotionId = pebble?.emotion?.id,
                domainId = pebble?.domains?.firstOrNull()?.id,
                valence = pebble?.valence,
                soulIds = pebble?.souls?.map { it.id }.orEmpty(),
                collectionId = pebble?.collections?.firstOrNull()?.id,
                visibility = pebble?.visibility ?: Visibility.SECRET,
            )
        }
    }
}

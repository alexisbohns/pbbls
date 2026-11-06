package app.pbbls.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime

/**
 * The encoder for both writers. Absent optional keys are left out rather than
 * sent as JSON null (A-16): the RPCs read null as absent anyway
 * (`private.jsonb_has`), and leaving them out matches what Swift's synthesized
 * encoder sends, so the two surfaces put identical bytes on the wire.
 * Non-null defaults are always encoded, so a key that equals its default
 * still reaches the server.
 */
internal val MomentWireJson =
    Json {
        explicitNulls = false
        encodeDefaults = true
    }

/** `save_moment(payload)`: create without [id], update your own with it. */
@Serializable
data class MomentSavePayload(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    @SerialName("happened_at")
    @Serializable(with = WholeSecondTimestampSerializer::class)
    val happenedAt: OffsetDateTime? = null,
    val snaps: List<PebbleSnapPayload>? = null,
) {
    companion object {
        /**
         * Capture creates, as iOS `SaveMomentPayload(capture:)`. A blank name is left out: a photo-only
         * moment is valid (D3). A blank description is left out too, and a typed one is trimmed.
         */
        fun capture(
            name: String,
            description: String,
            happenedAt: OffsetDateTime,
            snap: PebbleSnapPayload?,
        ): MomentSavePayload =
            MomentSavePayload(
                name = name.trim().ifEmpty { null },
                description = description.trim().ifEmpty { null },
                happenedAt = happenedAt,
                snaps = snap?.let { listOf(it) },
            )

        /**
         * Edit sends every moment key so the row ends up exactly as the form
         * shows it: `""` clears a text field (null would mean "keep"), and
         * `snaps` is the whole set, `[]` when the photo was removed (R5, R6).
         */
        fun edit(
            id: String,
            name: String,
            description: String,
            happenedAt: OffsetDateTime,
            snaps: List<PebbleSnapPayload>,
        ): MomentSavePayload =
            MomentSavePayload(
                id = id,
                name = name.trim(),
                description = description.trim(),
                happenedAt = happenedAt,
                snaps = snaps,
            )
    }
}

/**
 * `complete_pebble(payload)`: inserts or updates the caller's one pebble on
 * [momentId]. A key left null is omitted from the JSON, and the server keeps
 * what it is not sent; an empty list clears. [from] sends every key so a first
 * completion ends up exactly as answered; [edit] omits a set the user left as
 * prefilled. `new_souls`, `new_collections` and `cards` are never sent (A-6).
 */
@Serializable
data class PebbleCompletePayload(
    @SerialName("moment_id")
    val momentId: String,
    @SerialName("emotion_id")
    val emotionId: String? = null,
    val intensity: Int? = null,
    val positiveness: Int? = null,
    val visibility: Visibility? = null,
    @SerialName("domain_ids")
    val domainIds: List<String>? = null,
    @SerialName("soul_ids")
    val soulIds: List<String>? = null,
    @SerialName("collection_ids")
    val collectionIds: List<String>? = null,
) {
    companion object {
        /** A first completion: every key, so the join tables end up exactly as answered. */
        fun from(
            momentId: String,
            answers: PebbleAnswers,
        ): PebbleCompletePayload {
            val valence = requireNotNull(answers.valence) { "complete without a valence" }
            require(answers.domainIds.isNotEmpty()) { "complete without a domain" }
            return PebbleCompletePayload(
                momentId = momentId,
                emotionId = requireNotNull(answers.emotionId) { "complete without an emotion" },
                intensity = valence.intensity,
                positiveness = valence.positiveness,
                visibility = answers.visibility,
                domainIds = answers.domainIds,
                soulIds = answers.soulIds,
                collectionIds = listOfNotNull(answers.collectionId),
            )
        }

        /**
         * Edit pebble (#1099 D8, as iOS): a set left as prefilled is omitted, and absent means keep,
         * so a pebble with three domains survives an edit of its emotion. Touching the field replaces
         * the set with the pick. A draft with no prefill (nothing loaded) always sends.
         */
        fun edit(
            momentId: String,
            draft: PebbleDraft,
        ): PebbleCompletePayload {
            val full = from(momentId, draft.answers)
            val domainsUntouched = draft.prefilled && draft.domainId == draft.prefillDomainId
            val collectionUntouched = draft.prefilled && draft.collectionId == draft.prefillCollectionId
            return full.copy(
                domainIds = if (domainsUntouched) null else full.domainIds,
                collectionIds = if (collectionUntouched) null else full.collectionIds,
            )
        }
    }
}

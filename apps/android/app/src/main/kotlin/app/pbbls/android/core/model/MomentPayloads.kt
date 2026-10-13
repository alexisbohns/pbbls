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
 */
internal val MomentWireJson = Json { explicitNulls = false }

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
        /** Capture creates. A blank name is left out: a photo-only moment is valid (D3). */
        fun capture(
            name: String,
            happenedAt: OffsetDateTime,
            snap: PebbleSnapPayload?,
        ): MomentSavePayload =
            MomentSavePayload(
                name = name.trim().ifEmpty { null },
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
 * [momentId]. Every key is always sent, so an update rewrites the join tables
 * to exactly the answers (an empty list clears). `new_souls`,
 * `new_collections` and `cards` are never sent (A-6).
 */
@Serializable
data class PebbleCompletePayload(
    @SerialName("moment_id")
    val momentId: String,
    @SerialName("emotion_id")
    val emotionId: String,
    val intensity: Int,
    val positiveness: Int,
    val visibility: Visibility,
    @SerialName("domain_ids")
    val domainIds: List<String>,
    @SerialName("soul_ids")
    val soulIds: List<String>,
    @SerialName("collection_ids")
    val collectionIds: List<String>,
) {
    companion object {
        fun from(
            momentId: String,
            answers: PebbleAnswers,
        ): PebbleCompletePayload {
            val valence = requireNotNull(answers.valence) { "complete without a valence" }
            return PebbleCompletePayload(
                momentId = momentId,
                emotionId = requireNotNull(answers.emotionId) { "complete without an emotion" },
                intensity = valence.intensity,
                positiveness = valence.positiveness,
                visibility = answers.visibility,
                domainIds = listOf(requireNotNull(answers.domainId) { "complete without a domain" }),
                soulIds = answers.soulIds,
                collectionIds = listOfNotNull(answers.collectionId),
            )
        }
    }
}

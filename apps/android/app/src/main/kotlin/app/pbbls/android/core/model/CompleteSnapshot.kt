package app.pbbls.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A half-finished Complete flow, kept on the device per moment (A-5). Not a
 * wire shape: it never leaves the device, and server drafts are retired (D7).
 */
@Serializable
data class CompleteSnapshot(
    val intensity: Int? = null,
    val positiveness: Int? = null,
    @SerialName("emotion_id")
    val emotionId: String? = null,
    @SerialName("domain_ids")
    val domainIds: List<String> = emptyList(),
    /** Read-only: a snapshot stored before several domains (#1515) holds one. Never written. */
    @SerialName("domain_id")
    val legacyDomainId: String? = null,
    @SerialName("soul_ids")
    val soulIds: List<String> = emptyList(),
    @SerialName("collection_id")
    val collectionId: String? = null,
    val visibility: Visibility? = null,
) {
    /**
     * A valence counts: the step no longer preselects (D7), so one is always a pick. The grade never
     * does, since it arrives at its default and counting it would save every flow merely opened.
     */
    val isEmpty: Boolean
        get() =
            positiveness == null &&
                intensity == null &&
                emotionId == null &&
                domainIds.isEmpty() &&
                legacyDomainId == null &&
                soulIds.isEmpty() &&
                collectionId == null

    /** [known] null means reference data has not loaded: keep the ids rather than drop every one. */
    fun toAnswers(known: KnownDraftIds?): PebbleAnswers =
        PebbleAnswers(
            valence = Valence.orNull(positiveness, intensity),
            emotionId = emotionId,
            domainIds = domainIds.ifEmpty { listOfNotNull(legacyDomainId) },
            soulIds = if (known == null) soulIds else soulIds.filter { it in known.soulIds },
            collectionId = collectionId?.takeIf { known == null || it in known.collectionIds },
            visibility = visibility ?: Visibility.SECRET,
        )

    companion object {
        fun from(answers: PebbleAnswers): CompleteSnapshot =
            CompleteSnapshot(
                intensity = answers.valence?.intensity,
                positiveness = answers.valence?.positiveness,
                emotionId = answers.emotionId,
                domainIds = answers.domainIds,
                soulIds = answers.soulIds,
                collectionId = answers.collectionId,
                visibility = answers.visibility,
            )
    }
}

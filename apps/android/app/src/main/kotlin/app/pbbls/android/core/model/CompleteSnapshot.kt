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
    @SerialName("domain_id")
    val domainId: String? = null,
    @SerialName("soul_ids")
    val soulIds: List<String> = emptyList(),
    @SerialName("collection_id")
    val collectionId: String? = null,
    val visibility: Visibility? = null,
) {
    /** Valence and grade never count: both arrive seeded, and counting them would save every flow merely opened. */
    val isEmpty: Boolean
        get() = emotionId == null && domainId == null && soulIds.isEmpty() && collectionId == null

    /** [known] null means reference data has not loaded: keep the ids rather than drop every one. */
    fun toAnswers(known: KnownDraftIds?): PebbleAnswers =
        PebbleAnswers(
            valence = Valence.orNull(positiveness, intensity),
            emotionId = emotionId,
            domainId = domainId,
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
                domainId = answers.domainId,
                soulIds = answers.soulIds,
                collectionId = answers.collectionId,
                visibility = answers.visibility,
            )
    }
}

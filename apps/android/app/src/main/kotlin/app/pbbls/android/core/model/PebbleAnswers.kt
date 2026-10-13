package app.pbbls.android.core.model

/**
 * The pebble half of a moment, as the Complete flow and the edit form hold it.
 * Complete (spec D2) = valence + emotion + one domain; the rest is optional.
 */
data class PebbleAnswers(
    val valence: Valence? = null,
    val emotionId: String? = null,
    val domainId: String? = null,
    val soulIds: List<String> = emptyList(),
    val collectionId: String? = null,
    val visibility: Visibility = Visibility.SECRET,
) {
    val isComplete: Boolean get() = valence != null && emotionId != null && domainId != null
}

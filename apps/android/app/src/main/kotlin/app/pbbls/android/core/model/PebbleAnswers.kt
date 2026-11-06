package app.pbbls.android.core.model

/**
 * The pebble half of a moment, as the Complete flow and the edit form hold it.
 * Complete (spec D2, D10) = valence + emotion + one to three domains; the rest is optional.
 */
data class PebbleAnswers(
    val valence: Valence? = null,
    val emotionId: String? = null,
    /** In tap order, [DOMAIN_CAP] at most (D10). */
    val domainIds: List<String> = emptyList(),
    val soulIds: List<String> = emptyList(),
    val collectionId: String? = null,
    val visibility: Visibility = Visibility.SECRET,
) {
    val isComplete: Boolean get() = valence != null && emotionId != null && domainIds.isNotEmpty()

    companion object {
        const val DOMAIN_CAP = 3
    }
}

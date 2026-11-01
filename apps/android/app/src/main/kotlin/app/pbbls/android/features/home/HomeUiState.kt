package app.pbbls.android.features.home

/**
 * What Home draws (#1383). Not the app's sealed `Loading`/`Error`/`Content`: Home has no page-wide
 * spinner or error (iOS), and its sections load on their own, so it is one value whose sections
 * are each a [HomeSection]. Why, at length: [HomeViewModel]'s KDoc.
 *
 * @property name the signed-in user's display name, trimmed; "" until one lands (or when unset),
 *   which the greeting reads as "no name" and drops the comma.
 */
data class HomeUiState(
    val name: String = "",
)

/**
 * One section's read (#1383 D9–D13): each section of Home loads, fails and keeps its last value
 * alone. [Failed] carries no message: a failed decorative section draws nothing, and a section
 * that does show its failure maps the error to a string where it draws it.
 */
sealed interface HomeSection<out T> {
    data object Loading : HomeSection<Nothing>

    data object Failed : HomeSection<Nothing>

    data class Ready<T>(
        val value: T,
    ) : HomeSection<T>
}

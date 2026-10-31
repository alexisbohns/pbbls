package app.pbbls.android.features.home

import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.features.home.checklist.HomeChecklist
import app.pbbls.android.features.home.shortcuts.HomeShortcutsStyle

/**
 * What Home draws (#1383). Not the app's sealed `Loading`/`Error`/`Content`: Home has no page-wide
 * spinner or error (iOS), and its sections load on their own, so it is one value whose sections
 * are each a [HomeSection]. Why, at length: [HomeViewModel]'s KDoc.
 *
 * @property name the signed-in user's display name, trimmed; "" until one lands (or when unset),
 *   which the greeting reads as "no name" and drops the comma.
 * @property month the live month's wrap for the grid at the top (D8, D9): the device's copy first, then
 *   the network's; [HomeSection.Failed] (no copy, no network) and [HomeSection.Loading] draw no grid.
 * @property shortcuts how the quick actions look on this device (Settings → Home), never loading.
 * @property checklist your first five (D11), null until its first read lands; a failed read keeps
 *   the last one, as on iOS. The box draws only while [HomeChecklist.isShown].
 * @property claim the blue pebble's claim row: idle, sending, or "Couldn't claim it yet".
 */
data class HomeUiState(
    val name: String = "",
    val month: HomeSection<MonthWrap> = HomeSection.Loading,
    val shortcuts: HomeShortcutsStyle = HomeShortcutsStyle(),
    val checklist: HomeChecklist? = null,
    val claim: ClaimStatus = ClaimStatus.IDLE,
)

/** The claim row's state (D11): one value, so "claiming" and "failed" can never both hold. */
enum class ClaimStatus { IDLE, CLAIMING, FAILED }

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

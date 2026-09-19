package app.pbbls.android.features.profile

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.services.AchievementRecord
import app.pbbls.android.services.AchievementsServicing
import app.pbbls.android.ui.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import javax.inject.Inject

private const val TAG = "achievements-screen"

/**
 * What the achievements grid can be showing (#849).
 *
 * **This is the shape every screen's state takes from here on.** Three cases,
 * one of which carries the data, and *nothing else* — no `isLoading` beside a
 * `didFail` beside a list. The five booleans Settings currently juggles admit
 * 32 combinations of which four are real, and the render code spends its time
 * defending against the other 28; a sealed interface makes the impossible ones
 * unrepresentable instead of merely unreached.
 *
 * Derived values are **getters on the case, not constructor fields**. A field
 * would have to be recomputed and passed in at every construction site (and
 * would join `equals`, so two states with the same source data could compare
 * unequal); a `by lazy` getter is computed at most once per state value, which
 * is strictly better than the per-recomposition recompute it replaces.
 */
sealed interface AchievementsUiState {
    data object Loading : AchievementsUiState

    /**
     * [messageRes] rather than a message: a user-facing string is a resource,
     * never something an SDK handed us (D9). #850 replaces the `@StringRes` with
     * a sealed `DataError` mapped to one — the case stays, its payload changes.
     */
    data class Error(
        @StringRes val messageRes: Int,
    ) : AchievementsUiState

    data class Content(
        val catalog: List<AchievementRecord>,
        val unlockedAt: Map<String, OffsetDateTime>,
    ) : AchievementsUiState {
        /**
         * Families in ladder order, inactive-and-unearned badges filtered out.
         *
         * `AchievementsContent` used to compute this inline, so it ran on every
         * recomposition — twice per scroll frame — to produce the same answer.
         */
        internal val groups: List<AchievementFamilyGroup> by lazy {
            visibleFamilyGroups(catalog, unlockedAt.keys)
        }
    }
}

/**
 * State holder for the achievements grid (#849) — the worked reference for the
 * per-screen ViewModels that follow.
 *
 * Three things it buys, all of which were broken before:
 *
 * - **Rotation keeps the grid.** The load ran in a `LaunchedEffect` over
 *   `remember` state, so every rotate threw both away, re-ran
 *   `check_achievements()` and re-fetched catalog + unlocks. A `ViewModel`
 *   outlives the configuration change, so a rotate now recomposes against state
 *   that is already there.
 * - **Cancellation travels.** The old `catch (e: Exception)` swallowed
 *   `CancellationException`; [runCatchingCancellable] rethrows it first.
 * - **The load is testable.** [AchievementsServicing] is an interface, so
 *   `AchievementsViewModelTest` drives the success, failure and retry paths on a
 *   `StandardTestDispatcher` with no device and no live project.
 *
 * There is no `SavedStateHandle` here on purpose: everything on this screen is
 * server-derived and re-fetchable, so process death has nothing to restore that
 * a reload would not produce. It arrives where the user has typed something the
 * server has not seen — the record-flow draft, the Settings fields.
 */
@HiltViewModel
class AchievementsViewModel
    @Inject
    constructor(
        private val achievements: AchievementsServicing,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<AchievementsUiState>(AchievementsUiState.Loading)
        val uiState: StateFlow<AchievementsUiState> = _uiState.asStateFlow()

        /**
         * Held so a retry cancels the load it is retrying. Without it, a user
         * tapping Retry twice races two loads and the slower one wins — which is
         * the older one often enough to matter.
         */
        private var loadJob: Job? = null

        init {
            load()
        }

        fun retry() = load()

        private fun load() {
            loadJob?.cancel()
            // Synchronously, before the launch: `retry()` is a tap, and a tap that
            // leaves the error on screen until the dispatcher gets round to the
            // coroutine reads as a dead button.
            _uiState.value = AchievementsUiState.Loading
            loadJob =
                viewModelScope.launch {
                    // The retroactive grant precedes the read so a veteran's history
                    // is already unlocked when the grid renders. It swallows its own
                    // failures by contract, hence no result to fold.
                    achievements.checkIgnoringFailure()
                    _uiState.value =
                        runCatchingCancellable {
                            AchievementsUiState.Content(
                                catalog = achievements.loadCatalog(),
                                unlockedAt =
                                    achievements
                                        .loadUnlocks()
                                        .associate { it.achievementId to it.unlockedAt },
                            )
                        }.getOrElse {
                            Log.e(TAG, "achievements fetch failed", it)
                            AchievementsUiState.Error(R.string.achievements_load_error)
                        }
                }
        }
    }

package app.pbbls.android.features.path

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.PathServicing
import app.pbbls.android.core.data.PathStatsServicing
import app.pbbls.android.core.data.PebbleDraftsServicing
import app.pbbls.android.core.data.PebbleWriteServicing
import app.pbbls.android.core.model.Pebble
import app.pbbls.android.core.model.RippleSummary
import app.pbbls.android.core.model.WeekRollEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

private const val TAG = "path"

/**
 * The timeline's state (#849).
 *
 * Replaces `isLoading` + `didLoadFail` + `entries` + `focusedWeekStart` held as
 * four independent `remember`s, which between them could say "not loading, did
 * not fail, no entries, no focus" — a state the render code had to disambiguate
 * by hand (`didLoadFail || focused == null` rendered the *error* copy for an
 * empty-but-fine timeline).
 */
sealed interface PathUiState {
    data object Loading : PathUiState

    data class Error(
        @StringRes val messageRes: Int,
    ) : PathUiState

    data class Content(
        val entries: List<WeekRollEntry>,
        val focusedWeekStart: LocalDate,
        val today: LocalDate,
        val karma: Int?,
        val serverRipple: RippleSummary?,
        val draftCount: Int,
    ) : PathUiState {
        /**
         * The server's `active_today` compares against UTC `current_date`, so a
         * user past local midnight in a non-UTC zone would see "active today"
         * when they have not been. Recomputed against the device's day — ports
         * iOS `PathView.rippleWithLocalActiveToday` (the proper server-side fix
         * is the M22 follow-up).
         *
         * A getter, not a constructor field: the screen used to recompute this
         * on every recomposition, scanning every pebble of every week each time.
         */
        val ripple: RippleSummary? by lazy {
            serverRipple?.let { server ->
                val activeToday =
                    entries.any { entry ->
                        entry.pebbles.any {
                            it.createdAt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate() == today
                        }
                    }
                server.copy(activeToday = activeToday)
            }
        }
    }
}

/**
 * The delete confirmation and its failure notice — genuine dialogs, not
 * navigation destinations, so they stay here rather than becoming entries
 * (design D7).
 *
 * Detail, edit, the two composers and drafts used to live here too: five
 * independent flags plus two reload tokens, with a hand-written exclusion
 * (`isPresentingDrafts && !isPresentingCreate && !isPresentingFlow`) standing
 * in for what a sealed type should have made unrepresentable. #852 promotes
 * all five to [PebblesKey] entries on the shared back stack, which makes that
 * exclusion structurally impossible instead of merely discouraged — only one
 * entry is ever on top, so "drafts showing under a composer" is no longer a
 * state this class can even express.
 */
data class PathCovers(
    val pendingDeletion: Pebble? = null,
    val didDeleteFail: Boolean = false,
)

/**
 * State holder for the Path timeline (#849).
 *
 * What it fixes, beyond moving code:
 *
 * - **Rotation.** `PathScreen` re-fetched `path_pebbles()` on every
 *   configuration change, dropped the focused week and closed any open cover.
 *   All three now live here and outlive the rotation.
 * - **The delete write.** It ran in `rememberCoroutineScope`, so leaving the
 *   composition between "request sent" and "reload" cancelled the coroutine
 *   after the row was already gone server-side, leaving a deleted pebble on
 *   screen until the next cold start. It runs in `viewModelScope` now.
 * - **The per-recomposition derivation.** `rippleWithLocalActiveToday` scanned
 *   every pebble on every recomposition; it is a `by lazy` on the state.
 *
 * `today` is captured once at construction, as the screen's `remember` did — a
 * timeline that silently re-groups itself at midnight mid-session would be more
 * surprising than one that is stale until the next launch.
 */
@HiltViewModel
class PathViewModel
    @Inject
    constructor(
        private val pathService: PathServicing,
        private val stats: PathStatsServicing,
        private val drafts: PebbleDraftsServicing,
        private val writeService: PebbleWriteServicing,
    ) : ViewModel() {
        private val today = LocalDate.now()

        private var entries: List<WeekRollEntry> = emptyList()
        private var focusedWeekStart: LocalDate? = null
        private var draftCount: Int = 0
        private var hasFailed = false
        private var isLoaded = false

        private val _uiState = MutableStateFlow<PathUiState>(PathUiState.Loading)
        val uiState: StateFlow<PathUiState> = _uiState.asStateFlow()

        private val _covers = MutableStateFlow(PathCovers())
        val covers: StateFlow<PathCovers> = _covers.asStateFlow()

        private var loadJob: Job? = null
        private var resumeCount = 0

        init {
            load()
            // Stats rides its own coroutine so a slow or failed stats fetch never
            // delays the timeline (the iOS `.task { await stats.load() }` analog).
            viewModelScope.launch { stats.load() }
            // PathStatsService is a singleton holding Compose state that Profile
            // and the glyph picker also write to, so the timeline observes it
            // rather than keeping a copy — snapshotFlow is the bridge from
            // Compose state to a flow this ViewModel can fold into its own.
            viewModelScope.launch {
                snapshotFlow { stats.karma to stats.ripple }.collect { publish() }
            }
            refreshDraftCount()
        }

        /**
         * The timeline came back to the foreground.
         *
         * `PathScreen` used to keep its pebbles in `remember`, so pushing Profile
         * took it out of composition and popping back rebuilt it, re-running the
         * fetch. This ViewModel is scoped to the `NavBackStackEntry`, which
         * survives that round trip — so without this, editing a pebble's souls or
         * collections from a pushed screen, or deleting one there, left the
         * timeline showing what it showed before, until a write of its own
         * happened to reload it.
         *
         * **This is also what replaced the three write-completion mechanisms
         * (#852):** `detailReloadKey`, `draftsReloadKey` and the
         * `onFlowPublished`/`onFormCreated` callbacks all existed to notify this
         * ViewModel that a child cover had written something. Now that detail,
         * edit, both composers and drafts are entries on the same back stack,
         * *returning* to Path is itself the signal — publishing, editing and
         * deleting all reload the same way, through this one path.
         *
         * The first resume is skipped (`init` has already loaded); every later
         * one refreshes without the spinner. Deferred from PR #896, where the
         * same gap was fixed for the souls and collections lists.
         */
        fun onResumed() {
            resumeCount += 1
            if (resumeCount > 1) {
                reload()
                refreshDraftCount()
            }
        }

        // MARK: - Loading

        /** First load and explicit retry: shows the spinner. */
        fun retry() = load()

        private fun load() {
            loadJob?.cancel()
            _uiState.value = PathUiState.Loading
            isLoaded = false
            loadJob = viewModelScope.launch { fetch() }
        }

        /**
         * Refresh after a write. Deliberately does **not** return to
         * [PathUiState.Loading]: the timeline is already on screen and blanking it
         * to a spinner after a delete reads as a glitch (iOS `PathView.load()`
         * after the initial fetch).
         */
        fun reload() {
            loadJob?.cancel()
            loadJob = viewModelScope.launch { fetch() }
            viewModelScope.launch { stats.refresh() }
        }

        private suspend fun fetch() {
            runCatchingCancellable {
                WeekRollBuilder.build(pathService.loadPathPebbles(), ZoneId.systemDefault(), today)
            }.fold(
                onSuccess = { built ->
                    entries = built
                    focusedWeekStart = refocusedWeekStart(built, focusedWeekStart, today)
                    hasFailed = false
                    isLoaded = true
                },
                onFailure = {
                    Log.e(TAG, "path_pebbles load failed", it)
                    // A failed *refresh* keeps the timeline that is already up:
                    // replacing real pebbles with an error because a background
                    // reload lost the network would be a regression.
                    if (!isLoaded) hasFailed = true
                },
            )
            publish()
        }

        /**
         * Drafts count for the entry-point badge. Its own coroutine for the same
         * reason stats has one, and its failure is not the timeline's failure —
         * a badge that cannot be read is a zero, not an error screen.
         */
        fun refreshDraftCount() {
            viewModelScope.launch {
                draftCount =
                    runCatchingCancellable { drafts.count() }
                        .getOrElse {
                            Log.e(TAG, "draft count failed", it)
                            0
                        }
                publish()
            }
        }

        /** Rebuilds the exposed state from the pieces above. */
        private fun publish() {
            val focused = focusedWeekStart
            _uiState.value =
                when {
                    hasFailed -> PathUiState.Error(R.string.path_load_error)
                    !isLoaded -> PathUiState.Loading
                    // Unreachable in practice, and kept as a belt: `WeekRollBuilder`
                    // unions the current week in unconditionally, so a successful
                    // load always has at least one week and `refocusedWeekStart`
                    // always finds one. A user with no pebbles gets the current
                    // week, empty — not this branch. The old screen had the same
                    // fallback written as `didLoadFail || focused == null`.
                    focused == null -> PathUiState.Error(R.string.path_load_error)
                    else ->
                        PathUiState.Content(
                            entries = entries,
                            focusedWeekStart = focused,
                            today = today,
                            karma = stats.karma,
                            serverRipple = stats.ripple,
                            draftCount = draftCount,
                        )
                }
        }

        fun onFocusWeek(weekStart: LocalDate) {
            if (focusedWeekStart == weekStart) return
            focusedWeekStart = weekStart
            publish()
        }

        // MARK: - Delete

        fun requestDelete(pebble: Pebble) = _covers.update { it.copy(pendingDeletion = pebble) }

        fun cancelDelete() = _covers.update { it.copy(pendingDeletion = null) }

        fun dismissDeleteError() = _covers.update { it.copy(didDeleteFail = false) }

        /**
         * Confirmed delete. In `viewModelScope`, not the composition's: the row is
         * gone server-side the moment the request lands, and a cancelled coroutine
         * would skip the reload and leave it on screen.
         */
        fun confirmDelete() {
            val target = _covers.value.pendingDeletion ?: return
            _covers.update { it.copy(pendingDeletion = null) }
            viewModelScope.launch {
                runCatchingCancellable { writeService.delete(target.id) }
                    .fold(
                        onSuccess = { reload() },
                        onFailure = {
                            Log.e(TAG, "delete pebble failed", it)
                            _covers.update { covers -> covers.copy(didDeleteFail = true) }
                        },
                    )
            }
        }
    }

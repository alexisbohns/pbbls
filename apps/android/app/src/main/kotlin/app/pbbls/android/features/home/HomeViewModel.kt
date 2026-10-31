package app.pbbls.android.features.home

import android.util.Log
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.core.common.UiEffects
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.AchievementsServicing
import app.pbbls.android.core.data.HapticsPreference
import app.pbbls.android.core.data.HomeServicing
import app.pbbls.android.core.data.HomeShortcut
import app.pbbls.android.core.data.HomeShortcutsPreference
import app.pbbls.android.core.data.MomentReadServicing
import app.pbbls.android.core.data.PathWrapServicing
import app.pbbls.android.core.data.TabIntent
import app.pbbls.android.core.data.TabIntents
import app.pbbls.android.features.home.checklist.ChecklistTask
import app.pbbls.android.features.home.checklist.HomeChecklist
import app.pbbls.android.features.home.shortcuts.HomeShortcutsStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

private const val TAG = "home"

/**
 * Home's state (#1383; iOS `HomeModel`).
 *
 * **A deliberate exception to the `XUiState` convention** (`apps/android/CLAUDE.md`, screen
 * architecture): [HomeUiState] is one data class, not a sealed `Loading`/`Error`/`Content`. Home
 * makes four independent reads (the name, the month, the wraps, the checklist), none of which
 * owns the page: there is no page-wide spinner or error on iOS, the greeting draws before any of
 * them lands, and each keeps its last value when a later read fails. A single sealed state would
 * have to pick one read to stand for the page, or multiply the cases by four. So each section is
 * its own small sealed [HomeSection] inside the one state, which keeps the convention's point (no
 * flags beside data) section by section.
 *
 * Every resume reloads, the first included (`LifecycleResumeEffect` → [onResumed]): iOS's `.task`
 * re-runs whenever Home reappears, which is how a trip to another tab shows its effect here. There
 * is no splash hand-off: Android's splash already lets go on auth, and the greeting needs no read.
 */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val home: HomeServicing,
        /** The month grid's read (D9): `path_month_wrap`, the same cache entry as the Path's month page. */
        private val wraps: PathWrapServicing,
        /** Where a week tap leaves its focus for the Path (D10). */
        private val tabIntents: TabIntents,
        private val shortcuts: HomeShortcutsPreference,
        private val haptics: HapticsPreference,
        /** Your first five (D11): the retroactive check, the catalog, the unlocks and the blue pebble claim. */
        private val achievements: AchievementsServicing,
        /** `path_moments`, for the pebble task's newest bare moment (D11). */
        private val moments: MomentReadServicing,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HomeUiState(shortcuts = shortcutsStyle()))
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

        private val _effects = UiEffects<HomeEffect>(viewModelScope)

        /** Where a first-five task's call to action goes (D11), once its sheet is gone. */
        val effects: Flow<HomeEffect> = _effects.flow

        /** Pebbles' haptics switch, asked at each tap (#1242 D23). */
        val hapticsOn: Boolean get() = haptics.isOn

        init {
            // The quick actions' settings are Compose state on `SettingsStore`: a change made in
            // Settings reaches Home while it is alive, without waiting for a resume.
            viewModelScope.launch {
                snapshotFlow { shortcutsStyle() }
                    .collect { style -> _uiState.update { it.copy(shortcuts = style) } }
            }
        }

        /**
         * A quick action was tapped (#1376): it becomes the last used one, so the bento's square
         * goes to it next time (iOS `lastUsed = shortcut`). Shown at once, not only after the store
         * reports back.
         */
        fun onShortcutUsed(shortcut: HomeShortcut) {
            shortcuts.setLastUsed(shortcut)
            _uiState.update { it.copy(shortcuts = it.shortcuts.copy(lastUsed = shortcut)) }
        }

        private fun shortcutsStyle() = HomeShortcutsStyle(shortcuts.layout, shortcuts.stones, shortcuts.lastUsed)

        private var nameJob: Job? = null
        private var monthJob: Job? = null
        private var wrapsJob: Job? = null

        /** The zone the month's days are cut in, as the Path asks for it. */
        private val zone = ZoneId.systemDefault()

        fun onResumed() {
            if (nameJob?.isActive != true) nameJob = viewModelScope.launch { loadName() }
            if (monthJob?.isActive != true) monthJob = viewModelScope.launch { loadMonth() }
            // Every resume, as iOS's `.task`: a trip to the tab a task sent the user to ticks it here.
            viewModelScope.launch { loadChecklist() }
            retryWraps()
        }

        /** Reads the months again (D12): every resume, and the row's Try again. A read in flight is not doubled. */
        fun retryWraps() {
            if (wrapsJob?.isActive != true) wrapsJob = viewModelScope.launch { loadWraps() }
        }

        /**
         * One of Home's own flows wrote a moment (#1383 D7): the live month is read again, so the grid shows
         * it without waiting for a resume. The Path re-reads on its own resume (#852), which stands for iOS's
         * revision bump. The first five read again too, so a first moment ticks its row at once.
         */
        fun onMomentSaved() {
            monthJob?.cancel()
            monthJob = viewModelScope.launch { loadMonth() }
            viewModelScope.launch { loadChecklist() }
        }

        // MARK: - Your first five (D11)

        /** Bumped by every checklist read, so a slower earlier read never lands over a newer one (iOS). */
        private var checklistGeneration = 0

        /**
         * The retroactive check first, so a veteran's history is already ticked, then the catalog and the
         * unlocks side by side (iOS `HomeModel.loadChecklist`). A failed read keeps the last checklist.
         */
        private suspend fun loadChecklist() {
            val generation = ++checklistGeneration
            achievements.checkIgnoringFailure()
            runCatchingCancellable {
                coroutineScope {
                    val catalog = async { achievements.loadCatalog() }
                    val unlocks = async { achievements.loadUnlocks() }
                    HomeChecklist.fromUnlockIds(catalog.await(), unlocks.await())
                }
            }.fold(
                onSuccess = { checklist ->
                    if (generation == checklistGeneration) _uiState.update { it.copy(checklist = checklist) }
                },
                onFailure = { Log.w(TAG, "home checklist failed", it) },
            )
        }

        /**
         * Claims the blue pebble (D11). An empty grant is not success (the RPC's contract): the row stays
         * and says so. A grant has already played the unlock moment inside [AchievementsServicing.claimBluePebble],
         * so nothing here presents it again; the reload then finds the blue pebble and the box leaves.
         */
        fun claim() {
            if (_uiState.value.claim == ClaimStatus.CLAIMING) return
            _uiState.update { it.copy(claim = ClaimStatus.CLAIMING) }
            viewModelScope.launch {
                runCatchingCancellable { achievements.claimBluePebble() }.fold(
                    onSuccess = { granted ->
                        if (granted.isEmpty()) {
                            Log.e(TAG, "claim_blue_pebble granted nothing")
                            _uiState.update { it.copy(claim = ClaimStatus.FAILED) }
                        } else {
                            loadChecklist()
                            _uiState.update { it.copy(claim = ClaimStatus.IDLE) }
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "claim_blue_pebble failed", error)
                        _uiState.update { it.copy(claim = ClaimStatus.FAILED) }
                    },
                )
            }
        }

        /**
         * A task sheet's call to action (D11; iOS `HomeView.go`): the tab tasks leave their tab an intent
         * (D10) it takes on arrival; the pebble task completes the newest bare moment, or with none (or an
         * unreadable Path) records one chained into Complete.
         */
        fun onTaskGo(task: ChecklistTask) {
            when (task) {
                ChecklistTask.MOMENT -> _effects.emit(HomeEffect.OpenQuickMoment)
                ChecklistTask.PEBBLE -> viewModelScope.launch { _effects.emit(newestBare()) }
                ChecklistTask.COLLECTION -> {
                    tabIntents.post(TabIntent.CreateCollection)
                    _effects.emit(HomeEffect.OpenCollections)
                }
                ChecklistTask.SOUL -> {
                    tabIntents.post(TabIntent.CreateSoul)
                    _effects.emit(HomeEffect.OpenPeople)
                }
                ChecklistTask.GLYPH -> {
                    tabIntents.post(TabIntent.CarveGlyph)
                    _effects.emit(HomeEffect.OpenGlyphs)
                }
            }
        }

        /**
         * The row is only open for a user who has never completed a pebble, so the first bare moment of
         * the feed (newest first) is the answer and the read is bounded in practice.
         */
        private suspend fun newestBare(): HomeEffect =
            runCatchingCancellable { moments.loadPath() }.fold(
                onSuccess = { path -> path.firstOrNull { !it.isCompleted }?.let { HomeEffect.OpenComplete(it.id) } },
                onFailure = {
                    Log.e(TAG, "path_moments for the pebble task failed", it)
                    null
                },
            ) ?: HomeEffect.OpenCapture

        /**
         * A tap on a week of the grid (D10): leaves the Path a focus on [weekStart], which it takes on its
         * next resume. The screen then switches to the Path tab.
         */
        fun onWeek(weekStart: LocalDate) {
            tabIntents.post(TabIntent.FocusWeek(weekStart))
        }

        /**
         * [HomeServicing.displayName] folds its own failure into "" (logged there), so a blank answer
         * may be a failed read: it never replaces a name already shown, as iOS keeps its name when the
         * read throws.
         */
        private suspend fun loadName() {
            val name = home.displayName()
            if (name.isNotBlank()) _uiState.update { it.copy(name = name) }
        }

        /**
         * The live month (D9): the device's copy paints first when nothing of this month is shown, then the
         * network's read replaces it. A failure keeps what is shown; with nothing shown, the grid hides
         * ([HomeSection.Failed]): there is no error row for a decorative header.
         */
        private suspend fun loadMonth() {
            val month = YearMonth.now(zone)
            if (shownMonth() != month) {
                wraps.cachedMonthWrap(month, zone)?.let { kept -> _uiState.update { it.copy(month = HomeSection.Ready(kept)) } }
            }
            runCatchingCancellable { wraps.monthWrap(month, zone) }.fold(
                onSuccess = { wrap -> _uiState.update { it.copy(month = HomeSection.Ready(wrap)) } },
                onFailure = { error ->
                    Log.e(TAG, "path_month_wrap for $month failed", error)
                    if (shownMonth() != month) _uiState.update { it.copy(month = HomeSection.Failed) }
                },
            )
        }

        /**
         * The monthly wraps (D12). A failure keeps the months shown, marked [HomeWraps.stale]; with none shown
         * the section says it failed ([HomeSection.Failed]). An unfold in the wrap sheet reaches the card here,
         * through the server, on the resume that follows the sheet.
         */
        private suspend fun loadWraps() {
            runCatchingCancellable { home.wraps(zone) }.fold(
                onSuccess = { months -> _uiState.update { it.copy(wraps = HomeSection.Ready(HomeWraps(months))) } },
                onFailure = { error ->
                    Log.e(TAG, "home_wraps failed", error)
                    _uiState.update { state ->
                        val shown = state.wraps
                        state.copy(
                            wraps =
                                if (shown is HomeSection.Ready) {
                                    HomeSection.Ready(
                                        shown.value.copy(stale = true),
                                    )
                                } else {
                                    HomeSection.Failed
                                },
                        )
                    }
                },
            )
        }

        private fun shownMonth(): YearMonth? = (_uiState.value.month as? HomeSection.Ready)?.value?.month
    }

/** Where Home goes next (D11): a first-five task's call to action. */
sealed interface HomeEffect {
    data object OpenQuickMoment : HomeEffect

    data class OpenComplete(
        val momentId: String,
    ) : HomeEffect

    /** Capture, chained into Complete. */
    data object OpenCapture : HomeEffect

    data object OpenCollections : HomeEffect

    data object OpenPeople : HomeEffect

    /** Glyphs, on the You tab, on Mine. */
    data object OpenGlyphs : HomeEffect
}

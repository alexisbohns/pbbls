package app.pbbls.android.features.home

import android.util.Log
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.HapticsPreference
import app.pbbls.android.core.data.HomeServicing
import app.pbbls.android.core.data.HomeShortcut
import app.pbbls.android.core.data.HomeShortcutsPreference
import app.pbbls.android.core.data.PathWrapServicing
import app.pbbls.android.core.data.TabIntent
import app.pbbls.android.core.data.TabIntents
import app.pbbls.android.features.home.shortcuts.HomeShortcutsStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HomeUiState(shortcuts = shortcutsStyle()))
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

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

        /** The zone the month's days are cut in, as the Path asks for it. */
        private val zone = ZoneId.systemDefault()

        fun onResumed() {
            if (nameJob?.isActive != true) nameJob = viewModelScope.launch { loadName() }
            if (monthJob?.isActive != true) monthJob = viewModelScope.launch { loadMonth() }
        }

        /**
         * One of Home's own flows wrote a moment (#1383 D7): the live month is read again, so the grid shows
         * it without waiting for a resume. The Path re-reads on its own resume (#852), which stands for iOS's
         * revision bump; the first five's checklist reload (Part 9) joins here.
         */
        fun onMomentSaved() {
            monthJob?.cancel()
            monthJob = viewModelScope.launch { loadMonth() }
        }

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

        private fun shownMonth(): YearMonth? = (_uiState.value.month as? HomeSection.Ready)?.value?.month
    }

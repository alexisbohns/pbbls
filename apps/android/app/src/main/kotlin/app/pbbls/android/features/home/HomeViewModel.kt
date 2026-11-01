package app.pbbls.android.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.core.data.HomeServicing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HomeUiState())
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

        private var nameJob: Job? = null

        fun onResumed() {
            if (nameJob?.isActive == true) return
            nameJob = viewModelScope.launch { loadName() }
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
    }

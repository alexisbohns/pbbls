package app.pbbls.android.features.home.wraps

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.core.common.UiEffects
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.data.DisplayPreferences
import app.pbbls.android.core.data.HapticsPreference
import app.pbbls.android.core.data.HomeServicing
import app.pbbls.android.core.data.PathWrapServicing
import app.pbbls.android.core.data.TabIntent
import app.pbbls.android.core.data.TabIntents
import app.pbbls.android.core.model.home.HomeWrapMonth
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.features.home.HomeSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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

private const val TAG = "home-wrap"

/** What the wrap sheet shows (#1383 D12; iOS `HomeWrapSheet`'s states). */
sealed interface HomeWrapUiState {
    /** The months are being read, to learn whether this one is folded. */
    data object Loading : HomeWrapUiState

    /** The months could not be read: the error and Try again. */
    data class Error(
        @StringRes val message: Int,
    ) : HomeWrapUiState

    /** Folded: the summary, Unfold, three placeholders. [unfold] is the button's run. */
    data class Folded(
        val month: HomeWrapMonth,
        val unfold: Unfold = Unfold.IDLE,
    ) : HomeWrapUiState

    /** Revealed: the month's page, read cache first ([deck]). */
    data class Revealed(
        val month: HomeWrapMonth,
        val deck: HomeSection<MonthWrap> = HomeSection.Loading,
    ) : HomeWrapUiState
}

/** The Unfold button's run: [FAILED] shows "Couldn't load your months." under it, as iOS. */
enum class Unfold { IDLE, RUNNING, FAILED }

/** The sheet's one-shot effects. */
sealed interface HomeWrapEffect {
    /** The month is not among Home's months (gone since, or never there): the sheet closes itself. */
    data object Close : HomeWrapEffect
}

/**
 * One month's wrap sheet (#1383 D12; iOS `HomeWrapSheet`). [start] reads Home's months to learn the month's state:
 * folded, it offers Unfold (`reveal_wrap`), which flips the sheet to the revealed page once the server has recorded
 * it; revealed, it reads the month's wrap (`path_month_wrap`, the device's copy first, as the Path does). Home's
 * card follows through the server: Home reads its months again on its next resume.
 *
 * It also carries what the Path's page reads from the Path's ViewModel ([dayView], [hapticsOn]) and the week tap's
 * focus for the Path (D10).
 */
@HiltViewModel
class HomeWrapViewModel
    @Inject
    constructor(
        private val home: HomeServicing,
        private val wraps: PathWrapServicing,
        private val tabIntents: TabIntents,
        private val display: DisplayPreferences,
        private val haptics: HapticsPreference,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<HomeWrapUiState>(HomeWrapUiState.Loading)
        val uiState: StateFlow<HomeWrapUiState> = _uiState.asStateFlow()

        private val _effects = UiEffects<HomeWrapEffect>(viewModelScope)
        val effects: Flow<HomeWrapEffect> = _effects.flow

        /** The zone Home reads its months in, so the reveal names the same month. */
        private val zone = ZoneId.systemDefault()

        private var month: YearMonth? = null
        private var loadJob: Job? = null
        private var deckJob: Job? = null

        /** The day-dot view, the Path's (#1207 D9): Compose state, so a switch made in the page redraws it. */
        val dayView: DayView get() = display.dayView

        /** Pebbles' haptics switch (#1242 D23), read at each vibration of the intro. */
        val hapticsOn: Boolean get() = haptics.isOn

        fun onDayView(view: DayView) = display.setDayView(view)

        /** Opens the sheet on [month]; a second call for the same month (a recomposition, a rotation) does nothing. */
        fun start(month: YearMonth) {
            if (this.month == month) return
            this.month = month
            load(month)
        }

        /** Try again: the months, or the revealed month's page, whichever failed. */
        fun retry() {
            val month = month ?: return
            when (val state = _uiState.value) {
                is HomeWrapUiState.Error -> load(month)
                is HomeWrapUiState.Revealed -> if (state.deck is HomeSection.Failed) loadDeck(month)
                HomeWrapUiState.Loading, is HomeWrapUiState.Folded -> Unit
            }
        }

        /**
         * Unfold (iOS `HomeWrapSheet.unfold`): `reveal_wrap`, then the revealed page. A failure keeps the folded
         * month with "Couldn't load your months." under the button. A tap while one runs does nothing.
         */
        fun unfold() {
            val folded = _uiState.value as? HomeWrapUiState.Folded ?: return
            if (folded.unfold == Unfold.RUNNING) return
            _uiState.value = folded.copy(unfold = Unfold.RUNNING)
            viewModelScope.launch {
                runCatchingCancellable { home.reveal(folded.month.month, zone) }.fold(
                    onSuccess = { at -> show(folded.month.copy(revealedAt = at)) },
                    onFailure = { error ->
                        Log.e(TAG, "reveal_wrap for ${folded.month.month} failed", error)
                        _uiState.value = folded.copy(unfold = Unfold.FAILED)
                    },
                )
            }
        }

        /** A week tapped in the page (D10): leaves the Path its focus; the screen then closes and opens the Path. */
        fun onWeek(weekStart: LocalDate) {
            tabIntents.post(TabIntent.FocusWeek(weekStart))
        }

        private fun load(month: YearMonth) {
            loadJob?.cancel()
            _uiState.value = HomeWrapUiState.Loading
            loadJob =
                viewModelScope.launch {
                    runCatchingCancellable { home.wraps(zone) }.fold(
                        onSuccess = { months ->
                            val found = months.firstOrNull { it.month == month }
                            if (found == null) {
                                Log.w(TAG, "$month is not among Home's months; closing its sheet")
                                _effects.emit(HomeWrapEffect.Close)
                            } else {
                                show(found)
                            }
                        },
                        onFailure = { error ->
                            Log.e(TAG, "home_wraps for the sheet failed", error)
                            _uiState.value = HomeWrapUiState.Error(R.string.home_wraps_error)
                        },
                    )
                }
        }

        private fun show(month: HomeWrapMonth) {
            if (month.isFolded) {
                _uiState.value = HomeWrapUiState.Folded(month)
            } else {
                _uiState.value = HomeWrapUiState.Revealed(month)
                loadDeck(month.month)
            }
        }

        /** The month's page: the device's copy paints first, then the network's; a failure with nothing shown says so. */
        private fun loadDeck(month: YearMonth) {
            deckJob?.cancel()
            setDeck(HomeSection.Loading)
            deckJob =
                viewModelScope.launch {
                    wraps.cachedMonthWrap(month, zone)?.let { setDeck(HomeSection.Ready(it)) }
                    runCatchingCancellable { wraps.monthWrap(month, zone) }.fold(
                        onSuccess = { setDeck(HomeSection.Ready(it)) },
                        onFailure = { error ->
                            Log.e(TAG, "path_month_wrap for $month failed", error)
                            if (shownDeck() !is HomeSection.Ready) setDeck(HomeSection.Failed)
                        },
                    )
                }
        }

        private fun shownDeck(): HomeSection<MonthWrap>? = (_uiState.value as? HomeWrapUiState.Revealed)?.deck

        private fun setDeck(deck: HomeSection<MonthWrap>) {
            _uiState.update { state -> if (state is HomeWrapUiState.Revealed) state.copy(deck = deck) else state }
        }
    }

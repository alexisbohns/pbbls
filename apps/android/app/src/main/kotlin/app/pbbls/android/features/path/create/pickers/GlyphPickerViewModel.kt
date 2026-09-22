package app.pbbls.android.features.path.create.pickers

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.core.model.BuyGlyphResult
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphGridItem
import app.pbbls.android.features.glyph.services.GlyphMarketServicing
import app.pbbls.android.features.glyph.store.GlyphTab
import app.pbbls.android.services.PathStatsServicing
import app.pbbls.android.ui.UiEffects
import app.pbbls.android.ui.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "glyph-picker"

/**
 * What the picker's grid can be showing (#852) — the same three-case shape as
 * [app.pbbls.android.features.glyph.store.GlyphsUiState], deliberately: the
 * per-tab load/cache/error logic this replaces was a byte-for-byte duplicate
 * of the store's, just spelled with `remember`s instead of a `StateFlow`.
 *
 * The picker has no rename and no detail-drawer cover (there is nothing to
 * open — a tap either selects or opens the inline swap panel), so it carries
 * no `covers` sibling the way [app.pbbls.android.features.glyph.store.GlyphsListViewModel]
 * does.
 */
sealed interface GlyphPickerUiState {
    data object Loading : GlyphPickerUiState

    /** Carried so the tab bar keeps highlighting the tab that failed. */
    data class Error(
        val tab: GlyphTab,
        @StringRes val messageRes: Int,
    ) : GlyphPickerUiState

    data class Content(
        val tab: GlyphTab,
        val items: List<GlyphGridItem>,
        val karma: Int,
        val isLoadingTab: Boolean,
    ) : GlyphPickerUiState
}

/**
 * One-shot effects out of the picker (#852).
 *
 * A selection used to be a direct call into the `onSelected` lambda from
 * inside a composable event handler. Routing it through [UiEffects] instead
 * means the picker's own presentation reset (closing whichever content swap
 * is open) and the caller's callback both happen exactly once per selection,
 * survive a rotation the same way every other screen's effects do, and stay
 * out of [GlyphPickerUiState] — which is a value the UI re-reads on every
 * recomposition, not a queue.
 */
sealed interface GlyphPickerEffect {
    data class Selected(
        val glyph: Glyph,
    ) : GlyphPickerEffect
}

/**
 * State holder for the tabbed glyph picker (#852).
 *
 * Ports the shape of [app.pbbls.android.features.glyph.store.GlyphsListViewModel]
 * (#849) into what was, until now, `GlyphPickerSheet`'s own `remember`ed copy
 * of the same tab/load/cache/error bookkeeping — `itemsByTab`, `isLoading`,
 * `loadFailed` and `reloadToken` behind a `LaunchedEffect(tab, reloadToken)`.
 * That duplication is exactly what this ViewModel replaces; the on-demand,
 * current-tab-only load and the Commu `!owned` client filter are carried over
 * unchanged; only the storage changes.
 *
 * What genuinely differs from the store, and stays that way:
 * - **No rename, no detail drawer.** The picker's Commu tap opens the inline
 *   swap panel through [GlyphPickerState.buying] (still a presentation
 *   concern owned by the composable, not this ViewModel); the store opens
 *   [app.pbbls.android.features.glyph.store.GlyphDetailDrawer] instead.
 * - **A selection commits to the caller and closes the sheet**, so it goes
 *   out through [effects] rather than sitting in state — see
 *   [GlyphPickerEffect].
 * - **The carve studio is still an inline content swap here**, not the nav
 *   entry the store's "+" now opens (#852's `GlyphCarve` promotion covered the
 *   store only). A save therefore still prepends the fresh glyph to Mine and
 *   selects it optimistically — [onCarved] — instead of relying on a resume
 *   reload the way [app.pbbls.android.features.glyph.store.GlyphsListViewModel.onResumed]
 *   does.
 */
@HiltViewModel
class GlyphPickerViewModel
    @Inject
    constructor(
        // Not private: GlyphPickerContent reads it to hand GlyphSwapPanel its
        // buy dependency directly, rather than through a CompositionLocal (#852).
        val market: GlyphMarketServicing,
        private val stats: PathStatsServicing,
    ) : ViewModel() {
        private val itemsByTab = mutableMapOf<GlyphTab, List<GlyphGridItem>>()
        private var tab = GlyphTab.MINE
        private var isLoadingTab = false
        private var hasFailed = false

        private val _uiState = MutableStateFlow<GlyphPickerUiState>(GlyphPickerUiState.Loading)
        val uiState: StateFlow<GlyphPickerUiState> = _uiState.asStateFlow()

        private val effectsOut = UiEffects<GlyphPickerEffect>(viewModelScope)
        val effects: Flow<GlyphPickerEffect> = effectsOut.flow

        private var loadJob: Job? = null

        init {
            viewModelScope.launch { stats.load() }
            // The balance shown in the swap panel is spent by a purchase, and
            // Path/Profile write to the same singleton — observe it rather than
            // copying it, as the store does.
            viewModelScope.launch { snapshotFlow { stats.karma }.collect { publish() } }
            loadTab(GlyphTab.MINE)
        }

        fun onSelectTab(next: GlyphTab) {
            if (tab == next) return
            tab = next
            loadTab(next)
        }

        /** Re-runs the load for whichever tab is on screen — the old `reloadToken++`. */
        fun retry() = loadTab(tab)

        private fun loadTab(target: GlyphTab) {
            loadJob?.cancel()
            isLoadingTab = true
            hasFailed = false
            publish()
            loadJob =
                viewModelScope.launch {
                    runCatchingCancellable {
                        when (target) {
                            GlyphTab.MINE -> market.listMine()
                            GlyphTab.OWNED -> market.listOwned()
                            // The store shows every community glyph including the
                            // caller's own owned ones; the picker additionally
                            // client-filters `!owned` (design D10) since an owned
                            // glyph belongs under Owned, not up for sale again.
                            GlyphTab.COMMU -> market.listCommunity().filter { !it.owned }
                        }
                    }.fold(
                        onSuccess = { itemsByTab[target] = it },
                        onFailure = {
                            Log.e(TAG, "glyph picker tab load failed: $target", it)
                            // A stale cache keeps rendering; only an empty tab
                            // becomes the error state.
                            if (itemsByTab[target].isNullOrEmpty()) hasFailed = true
                        },
                    )
                    isLoadingTab = false
                    publish()
                }
        }

        private fun publish() {
            val cached = itemsByTab[tab].orEmpty()
            _uiState.value =
                when {
                    hasFailed && cached.isEmpty() -> GlyphPickerUiState.Error(tab, R.string.create_glyph_load_error)
                    isLoadingTab && cached.isEmpty() && !hasFailed -> GlyphPickerUiState.Loading
                    else ->
                        GlyphPickerUiState.Content(
                            tab = tab,
                            items = cached,
                            karma = stats.karma ?: 0,
                            isLoadingTab = isLoadingTab,
                        )
                }
        }

        /** Every select leaves the picker showing its grid — the caller resets on the effect. */
        fun selectGlyph(glyph: Glyph) = effectsOut.emit(GlyphPickerEffect.Selected(glyph))

        /**
         * A carve landed. Optimistically prepends the fresh glyph to the Mine
         * cache (there is no round trip to reload it from, unlike the store,
         * which reloads on resume instead) and selects it, closing the sheet.
         */
        fun onCarved(glyph: Glyph) {
            val fresh = GlyphGridItem(glyph = glyph, price = 0, owned = false, createdAt = null, acquiredAt = null)
            itemsByTab[GlyphTab.MINE] = listOf(fresh) + itemsByTab[GlyphTab.MINE].orEmpty()
            if (tab == GlyphTab.MINE) publish()
            selectGlyph(glyph)
        }

        /**
         * A purchase landed in the inline swap panel. Record-only, mirroring
         * [app.pbbls.android.features.glyph.store.GlyphsListViewModel.onPurchased]'s
         * balance half — the picker does not maintain a Commu/Owned cache
         * invalidation because a successful buy immediately calls [selectGlyph]
         * and closes the sheet, so there is no stale list left on screen to fix
         * up.
         */
        fun onPurchaseRecorded(result: BuyGlyphResult) {
            stats.applyKarmaBalance(result.balance)
            publish()
        }
    }

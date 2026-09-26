package app.pbbls.android.features.glyph.store

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.GlyphMarketServicing
import app.pbbls.android.core.data.GlyphService
import app.pbbls.android.core.data.GlyphServicing
import app.pbbls.android.core.data.PathStatsServicing
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphGridItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "glyph-store"

/**
 * The glyph store's three tabs (#849).
 *
 * The per-tab cache is deliberate and ported as-is: switching back to a tab
 * already fetched shows it immediately and refetches underneath, so [Content]
 * carries both the cached items and whether a fetch is in flight over them.
 * [Error] is reached only when the tab has nothing cached *and* the fetch
 * failed — a failed refresh over a populated tab keeps the glyphs on screen.
 */
sealed interface GlyphsUiState {
    /**
     * Carries its tab for the same reason [Error] does: the first visit to a
     * tab passes through here, and a tab-less Loading made the bar flash Mine
     * on every first switch (#855).
     */
    data class Loading(
        val tab: GlyphTab,
    ) : GlyphsUiState

    data class Error(
        /** Carried so the tab bar keeps highlighting the tab that failed. */
        val tab: GlyphTab,
        @StringRes val messageRes: Int,
    ) : GlyphsUiState

    data class Content(
        val tab: GlyphTab,
        val items: List<GlyphGridItem>,
        val karma: Int,
        val isLoadingTab: Boolean,
    ) : GlyphsUiState
}

/**
 * The rename dialog. The glyph detail used to be a drawer here too; it is its
 * own entry now (`PebblesKey.GlyphDetail`, #940).
 */
data class GlyphsCovers(
    val renaming: Glyph? = null,
    val didRenameFail: Boolean = false,
)

/**
 * State holder for the glyph store (#849).
 *
 * **The write this exists for is a purchase, and it was the worst cancellation
 * hole in the app.** `buy_glyph` spends the buyer's karma, inserts the
 * entitlement and credits the creator in one transaction. The buy ran in
 * `SlideToConfirm`'s `rememberCoroutineScope`, and everything that recorded it
 * on the client ran *after* the call returned: the drawer's own `isOwned`, and
 * an `onSwapped` callback on the host that applied the new balance, dropped the
 * glyph from the Community tab and invalidated the Owned cache.
 *
 * Dismiss the drawer while the request is in flight and all of it is cancelled.
 * The karma is spent, the creator is paid, the entitlement exists — and the
 * screen shows the same balance and the same unowned glyph. The natural
 * response is to buy again, which `buy_glyph` rejects with `already_owned`: an
 * error message, for something the user has in fact just paid for and owns.
 *
 * The fix lives in [GlyphSwapPanel], not here, because that panel has a second
 * host — the composer's glyph picker — and a guarantee only one of two hosts
 * gets is not a guarantee. The panel wraps the call *and* its host callback in
 * `withContext(NonCancellable)`. `GlyphPickerSheet` keeps its own duplicated
 * copy of the store's state and is a migration of its own, not folded in here.
 *
 * **This list learns of a purchase from `GlyphMarketServicing.purchases`
 * (#940).** The panel now lives in its own entry, `GlyphDetail`, which on a
 * large screen sits beside this list — so the list never pauses and no resume
 * refresh fires. The market service announces every successful buy, from any
 * host, and [recordPurchase] fixes up this screen's caches from it. The
 * balance is not written here: the host that ran the buy records it inside the
 * uncancellable section (`GlyphDetailViewModel.onRecorded`, or the picker's
 * own), and this screen observes the shared stats like every other.
 *
 * **The carve cover is gone (#852).** It used to prepend the fresh
 * glyph to Mine and switch straight to it on save, an optimistic update with no
 * round trip. `GlyphCarve` is a separate entry now, with no callback back into
 * this instance; [onResumed] (#852) reloads whichever tab is on screen when
 * the trip back lands, which restores correctness (a carved glyph is visible
 * once you're on Mine) but not the optimism — carving while on Owned or Commu
 * reloads that tab, not Mine, and there is no auto-switch to Mine on return.
 * Restoring that is a product behaviour that needs a real mechanism (a nav
 * result) and is out of scope here.
 */
@HiltViewModel
class GlyphsListViewModel
    @Inject
    constructor(
        private val market: GlyphMarketServicing,
        private val glyphService: GlyphServicing,
        private val stats: PathStatsServicing,
    ) : ViewModel() {
        private val itemsByTab = mutableMapOf<GlyphTab, List<GlyphGridItem>>()
        private var tab = GlyphTab.MINE
        private var isLoadingTab = false
        private var hasFailed = false

        /**
         * Every glyph bought while this screen lived (#940). Beside the detail
         * the grid is live, so a Community load answered before `buy_glyph`
         * committed can land after [recordPurchase]; filtering every write to
         * the Community cache by this keeps the glyph from coming back.
         */
        private val purchasedIds = mutableSetOf<String>()

        private val _uiState = MutableStateFlow<GlyphsUiState>(GlyphsUiState.Loading(GlyphTab.MINE))
        val uiState: StateFlow<GlyphsUiState> = _uiState.asStateFlow()

        private val _covers = MutableStateFlow(GlyphsCovers())
        val covers: StateFlow<GlyphsCovers> = _covers.asStateFlow()

        private var loadJob: Job? = null
        private var resumeCount = 0

        init {
            viewModelScope.launch { stats.load() }
            // The balance is shown in the drawer and spent by a purchase, and
            // Path and Profile write to the same singleton — so observe it
            // rather than copying it, as they do.
            viewModelScope.launch { snapshotFlow { stats.karma }.collect { publish() } }
            viewModelScope.launch { market.purchases.collect { recordPurchase(it.glyphId) } }
            loadTab(GlyphTab.MINE)
        }

        fun onSelectTab(next: GlyphTab) {
            if (tab == next) return
            tab = next
            loadTab(next)
        }

        /**
         * The destination came back to the foreground.
         *
         * Reloads the tab currently on screen — the same per-tab refetch
         * [loadTab] already does for a tab switch, just re-run on the tab you
         * never left. This is what picks up a glyph carved in `GlyphCarve`
         * (#852 removed the optimistic prepend-and-switch). The first
         * resume is skipped because `init` has already loaded Mine.
         */
        fun onResumed() {
            resumeCount += 1
            if (resumeCount > 1) loadTab(tab)
        }

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
                            GlyphTab.COMMU -> market.listCommunity()
                        }
                    }.fold(
                        onSuccess = { items ->
                            itemsByTab[target] =
                                if (target == GlyphTab.COMMU) items.filter { it.id !in purchasedIds } else items
                        },
                        onFailure = {
                            Log.e(TAG, "glyph tab load failed: $target", it)
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
                    hasFailed && cached.isEmpty() -> GlyphsUiState.Error(tab, R.string.glyphs_load_error)
                    isLoadingTab && cached.isEmpty() && !hasFailed -> GlyphsUiState.Loading(tab)
                    else ->
                        GlyphsUiState.Content(
                            tab = tab,
                            items = cached,
                            karma = stats.karma ?: 0,
                            isLoadingTab = isLoadingTab,
                        )
                }
        }

        // MARK: - Rename

        /** Clears the previous failure: the banner belongs to the attempt, not the screen. */
        fun requestRename(glyph: Glyph) = _covers.update { it.copy(renaming = glyph, didRenameFail = false) }

        fun cancelRename() = _covers.update { it.copy(renaming = null) }

        fun dismissRenameError() = _covers.update { it.copy(didRenameFail = false) }

        /**
         * Optimistic rename: the caption changes immediately and the server's own
         * normalization replaces it on success. A failure restores the list it
         * started from rather than the row, so two renames racing cannot leave a
         * half-applied caption.
         *
         * `NonCancellable` because the restore-on-failure is the only thing that
         * undoes the optimism: losing it leaves a name on screen the server never
         * accepted.
         */
        fun confirmRename(draft: String) {
            val glyph = _covers.value.renaming ?: return
            _covers.update { it.copy(renaming = null) }

            val original = itemsByTab[GlyphTab.MINE].orEmpty()
            val optimistic = glyph.copy(name = GlyphService.normalizedName(draft))
            itemsByTab[GlyphTab.MINE] =
                original.map { if (it.id == glyph.id) it.copy(glyph = optimistic) else it }
            publish()

            viewModelScope.launch {
                withContext(NonCancellable) {
                    runCatchingCancellable { glyphService.updateName(glyphId = glyph.id, name = draft) }
                        .fold(
                            onSuccess = { server ->
                                itemsByTab[GlyphTab.MINE] =
                                    itemsByTab[GlyphTab.MINE].orEmpty().map {
                                        if (it.id == glyph.id) it.copy(glyph = server) else it
                                    }
                            },
                            onFailure = {
                                Log.e(TAG, "glyph rename failed", it)
                                itemsByTab[GlyphTab.MINE] = original
                                _covers.update { covers -> covers.copy(didRenameFail = true) }
                            },
                        )
                    publish()
                }
            }
        }

        // MARK: - Purchases

        /**
         * A purchase landed, here or in the composer's picker. Drops the glyph
         * from Community and makes Owned refetch. The balance is already
         * recorded by the host that ran the buy (see the class KDoc).
         *
         * Owned refetches lazily on its next visit (iOS parity), unless it is
         * the tab on screen: beside the detail the grid stays live, and
         * dropping its cache there would blank it.
         */
        private fun recordPurchase(glyphId: String) {
            purchasedIds += glyphId
            itemsByTab[GlyphTab.COMMU] =
                itemsByTab[GlyphTab.COMMU].orEmpty().filter { it.id !in purchasedIds }
            if (tab == GlyphTab.OWNED) {
                loadTab(GlyphTab.OWNED)
            } else {
                itemsByTab.remove(GlyphTab.OWNED)
                publish()
            }
        }
    }

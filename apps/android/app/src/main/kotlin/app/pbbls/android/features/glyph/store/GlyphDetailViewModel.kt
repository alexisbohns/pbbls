package app.pbbls.android.features.glyph.store

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.core.data.GlyphMarketServicing
import app.pbbls.android.core.data.PathStatsServicing
import app.pbbls.android.core.model.BuyGlyphResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The glyph detail entry (#940). The item arrives in the key, so there is
 * nothing to load: this holds the buyer's balance and records a purchase.
 *
 * The buy itself stays in [GlyphSwapPanel], which the composer's glyph picker
 * also hosts; [onRecorded] runs inside its uncancellable section and only
 * records. It is the one place the store writes the new balance. The store
 * list learns of the purchase from `GlyphMarketServicing.purchases`, not from
 * here, and does not write the balance again.
 */
@HiltViewModel
class GlyphDetailViewModel
    @Inject
    constructor(
        // Not private: GlyphDetailScreen hands it to GlyphSwapPanel, which runs
        // the buy (#852 passes the dependency directly, not through a local).
        val market: GlyphMarketServicing,
        private val stats: PathStatsServicing,
    ) : ViewModel() {
        /** Path and Profile write to the same singleton, so observe it rather than copy it. */
        val balance: StateFlow<Int> =
            snapshotFlow { stats.karma ?: 0 }
                .stateIn(viewModelScope, SharingStarted.Eagerly, stats.karma ?: 0)

        fun onRecorded(result: BuyGlyphResult) = stats.applyKarmaBalance(result.balance)
    }

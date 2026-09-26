package app.pbbls.android.features.glyph.store

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.core.data.GlyphMarketServicing
import app.pbbls.android.core.data.PathStatsServicing
import app.pbbls.android.core.model.BuyGlyphResult
import app.pbbls.android.core.model.GlyphGridItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.OffsetDateTime
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
 *
 * **It also remembers that the glyph is now owned.** The key's item still says
 * unowned after a buy, and the panel's own state is rebuilt from it whenever
 * the entry is (rotation, resize, a sheet becoming a pane). Rebuilt from the
 * key alone it offers the swap again, and the second buy fails with
 * `already_owned` — the #849 failure. So the acquisition lives in this entry's
 * [SavedStateHandle], and [shown] is the item as this device knows it.
 */
@HiltViewModel
class GlyphDetailViewModel
    @Inject
    constructor(
        // Not private: GlyphDetailScreen hands it to GlyphSwapPanel, which runs
        // the buy (#852 passes the dependency directly, not through a local).
        val market: GlyphMarketServicing,
        private val stats: PathStatsServicing,
        private val savedState: SavedStateHandle,
    ) : ViewModel() {
        /** Path and Profile write to the same singleton, so observe it rather than copy it. */
        val balance: StateFlow<Int> =
            snapshotFlow { stats.karma ?: 0 }
                .stateIn(viewModelScope, SharingStarted.Eagerly, stats.karma ?: 0)

        /**
         * A purchase of [item] landed. Runs inside the panel's uncancellable
         * section, so it only records: the balance, and the acquisition time
         * (the client's now, as iOS stamps it).
         */
        fun onRecorded(
            item: GlyphGridItem,
            result: BuyGlyphResult,
        ) {
            stats.applyKarmaBalance(result.balance)
            savedState[acquiredKey(item.id)] = OffsetDateTime.now().toString()
        }

        /** [item], owned if a purchase of it was recorded here. */
        fun shown(item: GlyphGridItem): GlyphGridItem {
            val acquired = savedState.get<String>(acquiredKey(item.id)) ?: return item
            return item.copy(owned = true, acquiredAt = OffsetDateTime.parse(acquired))
        }

        private fun acquiredKey(glyphId: String) = "acquired_$glyphId"
    }

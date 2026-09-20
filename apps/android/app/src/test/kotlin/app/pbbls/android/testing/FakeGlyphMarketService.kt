package app.pbbls.android.testing

import app.pbbls.android.features.glyph.models.BuyGlyphResult
import app.pbbls.android.features.glyph.models.GlyphGridItem
import app.pbbls.android.features.glyph.services.GlyphMarketServicing
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [GlyphMarketServicing] (#849) — the store's three tabs and the one
 * call in this app that spends the user's karma.
 */
class FakeGlyphMarketService(
    var mine: List<GlyphGridItem> = emptyList(),
    var owned: List<GlyphGridItem> = emptyList(),
    var community: List<GlyphGridItem> = emptyList(),
    /** What [buy] returns. */
    var buyResult: BuyGlyphResult = BuyGlyphResult(entitlementId = "ent-1", balance = 0),
) : GlyphMarketServicing {
    var mineCount = 0
        private set

    var ownedCount = 0
        private set

    var communityCount = 0
        private set

    /** Every glyph id passed to [buy], oldest first. */
    val buyCalls = mutableListOf<String>()

    /** Awaited by [buy] when set, to hold a purchase open at the server call. */
    var buyGate: CompletableDeferred<Unit>? = null

    private val armed = ArmedFailure()

    /** Thrown by the next call, then cleared. */
    var failNext: Exception?
        get() = armed.next
        set(value) {
            armed.next = value
        }

    override suspend fun listMine(): List<GlyphGridItem> {
        mineCount += 1
        armed.fire()
        return mine
    }

    override suspend fun listOwned(): List<GlyphGridItem> {
        ownedCount += 1
        armed.fire()
        return owned
    }

    override suspend fun listCommunity(): List<GlyphGridItem> {
        communityCount += 1
        armed.fire()
        return community
    }

    override suspend fun buy(glyphId: String): BuyGlyphResult {
        buyCalls += glyphId
        buyGate?.await()
        armed.fire()
        return buyResult
    }
}

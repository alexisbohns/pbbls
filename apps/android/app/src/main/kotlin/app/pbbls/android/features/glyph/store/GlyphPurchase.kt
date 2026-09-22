package app.pbbls.android.features.glyph.store

import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.GlyphMarketServicing
import app.pbbls.android.core.model.BuyGlyphResult
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * The one call in this app that spends the user's karma, and the guarantee it
 * needs (#849).
 *
 * `buy_glyph` spends the buyer's karma, inserts the entitlement and credits the
 * creator in a single transaction. Once it has left the device there is no
 * outcome in which stopping is safe, so the call **and** the client-side record
 * of it run under `withContext(NonCancellable)`.
 *
 * Before this, the buy ran in `SlideToConfirm`'s `rememberCoroutineScope` and
 * every line that recorded it came after the call returned. Dismissing the
 * sheet mid-request cancelled the lot: the karma was gone, the creator paid,
 * the entitlement real — and the screen showed the same balance and the same
 * unowned glyph. Buying again then failed with `already_owned`, an error for
 * something the user had just paid for.
 *
 * **[onRecorded] is inside the uncancellable section; anything else is not.**
 * That split is the point. Recording a purchase (the new balance, the caches
 * that now disagree with the server) must survive the user walking away.
 * *Reacting* to one — selecting the glyph into a form, handing control back to
 * a caller — must not, or dismissing a picker mid-buy silently edits a form
 * the user has already left. The panel calls [onSwapped] for that, outside.
 *
 * Extracted from the composable so the guarantee has a test: a composable's
 * `suspend` lambda cannot be driven from a JVM test, and "the worst
 * cancellation hole in the app" should not be the one thing taken on trust.
 */
object GlyphPurchase {
    /**
     * Buy [glyphId] and record it. Returns the result, or null if the server
     * refused — `SlideToConfirm` parks or springs back its thumb on that.
     *
     * [onRecorded] runs before this returns and inside the uncancellable
     * section, so it must only record. It must not navigate, dismiss or select.
     */
    suspend fun buyAndRecord(
        market: GlyphMarketServicing,
        glyphId: String,
        onRecorded: (BuyGlyphResult) -> Unit,
        onError: (Throwable) -> Unit,
    ): BuyGlyphResult? =
        withContext(NonCancellable) {
            runCatchingCancellable { market.buy(glyphId) }
                .fold(
                    onSuccess = { result ->
                        onRecorded(result)
                        result
                    },
                    onFailure = { error ->
                        onError(error)
                        null
                    },
                )
        }
}

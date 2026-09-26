package app.pbbls.android.features.glyph.store

import app.pbbls.android.core.data.GlyphPurchased
import app.pbbls.android.core.model.BuyGlyphResult
import app.pbbls.android.testing.FakeGlyphMarketService
import app.pbbls.android.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * **The worst cancellation hole in the app, and the only test that proves it
 * closed.**
 *
 * `buy_glyph` spends the buyer's karma, inserts the entitlement and credits the
 * creator in one transaction. The buy runs in `SlideToConfirm`'s
 * `rememberCoroutineScope`, so the sheet being dismissed cancels it — and every
 * line that records the purchase on the client used to come after the call
 * returned.
 *
 * The composable itself cannot be driven from a JVM test, which is exactly why
 * [GlyphPurchase] exists as a plain suspend function: the guarantee is testable
 * rather than taken on trust.
 */
class GlyphPurchaseTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `a landed purchase records before returning`() =
        runTest {
            val market = FakeGlyphMarketService(buyResult = BuyGlyphResult("ent-1", balance = 90))
            val recorded = mutableListOf<BuyGlyphResult>()

            val result =
                GlyphPurchase.buyAndRecord(
                    market = market,
                    glyphId = "g1",
                    onRecorded = { recorded += it },
                    onError = { error("unexpected: $it") },
                )

            assertEquals(listOf("g1"), market.buyCalls)
            assertEquals(90, result?.balance)
            assertEquals(listOf(90), recorded.map { it.balance })
        }

    /**
     * The regression, reproduced: the gesture's scope dies mid-request. The
     * karma is spent either way — so the record of it has to land, or the user
     * has paid for a glyph the screen still shows as unowned.
     */
    @Test
    fun `cancelling the caller still records the purchase`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val market = FakeGlyphMarketService(buyResult = BuyGlyphResult("ent-1", balance = 90))
            val gate = CompletableDeferred<Unit>()
            market.buyGate = gate
            val recorded = mutableListOf<BuyGlyphResult>()

            // Stands in for SlideToConfirm's rememberCoroutineScope.
            val gestureScope = CoroutineScope(dispatcher)
            gestureScope.launch {
                GlyphPurchase.buyAndRecord(
                    market = market,
                    glyphId = "g1",
                    onRecorded = { recorded += it },
                    onError = { error("unexpected: $it") },
                )
            }
            advanceUntilIdle()

            // In flight: the request has left the device.
            assertEquals(listOf("g1"), market.buyCalls)
            assertTrue(recorded.isEmpty())

            // The user swipes the sheet away.
            gestureScope.cancel()
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                "the purchase must still be recorded, or the karma is spent for nothing",
                listOf(90),
                recorded.map { it.balance },
            )
        }

    @Test
    fun `a refused purchase reports and records nothing`() =
        runTest {
            val market = FakeGlyphMarketService()
            market.failNext = IOException("offline")
            val recorded = mutableListOf<BuyGlyphResult>()
            val errors = mutableListOf<Throwable>()

            val result =
                GlyphPurchase.buyAndRecord(
                    market = market,
                    glyphId = "g1",
                    onRecorded = { recorded += it },
                    onError = { errors += it },
                )

            assertNull(result)
            assertTrue(recorded.isEmpty())
            assertEquals(1, errors.size)
        }

    /**
     * The store list learns of a purchase from `purchases` (#940), whichever
     * host ran it. On the fake: the real service's `buy` goes through the
     * Supabase client, which a JVM test cannot construct.
     */
    @Test
    fun `a landed purchase reaches purchases collectors`() =
        runTest {
            val market = FakeGlyphMarketService(buyResult = BuyGlyphResult("ent-1", balance = 90))
            val heard = mutableListOf<GlyphPurchased>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { market.purchases.collect { heard += it } }

            GlyphPurchase.buyAndRecord(market = market, glyphId = "g1", onRecorded = {}, onError = { error("unexpected: $it") })

            assertEquals(listOf(GlyphPurchased("g1", BuyGlyphResult("ent-1", balance = 90))), heard)
        }

    @Test
    fun `a refused purchase announces nothing`() =
        runTest {
            val market = FakeGlyphMarketService()
            market.failNext = IOException("offline")
            val heard = mutableListOf<GlyphPurchased>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { market.purchases.collect { heard += it } }

            GlyphPurchase.buyAndRecord(market = market, glyphId = "g1", onRecorded = {}, onError = {})

            assertTrue(heard.isEmpty())
        }
}

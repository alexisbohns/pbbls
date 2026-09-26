package app.pbbls.android.features.glyph.store

import androidx.compose.runtime.snapshots.Snapshot
import app.pbbls.android.core.model.BuyGlyphResult
import app.pbbls.android.testing.FakeGlyphMarketService
import app.pbbls.android.testing.FakePathStatsService
import app.pbbls.android.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The glyph detail entry's balance and purchase record (#940). */
class GlyphDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `the balance is the shared karma`() =
        runTest {
            val stats = FakePathStatsService(karma = 42)
            val viewModel = GlyphDetailViewModel(FakeGlyphMarketService(), stats)
            advanceUntilIdle()

            assertEquals(42, viewModel.balance.value)
        }

    /**
     * The store's half of recording a purchase: the balance lands in the
     * shared stats (Path and Profile read the same singleton), and the
     * detail's own balance follows it.
     */
    @Test
    fun `a recorded purchase applies the new balance`() =
        runTest {
            val stats = FakePathStatsService(karma = 42)
            val viewModel = GlyphDetailViewModel(FakeGlyphMarketService(), stats)
            advanceUntilIdle()

            viewModel.onRecorded(BuyGlyphResult(entitlementId = "e1", balance = 32))
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            assertEquals("the new balance must reach the shared stats", 32, stats.karma)
            assertEquals(32, viewModel.balance.value)
        }

    @Test
    fun `a balance written elsewhere reaches the detail`() =
        runTest {
            val stats = FakePathStatsService(karma = null)
            val viewModel = GlyphDetailViewModel(FakeGlyphMarketService(), stats)
            advanceUntilIdle()
            assertEquals(0, viewModel.balance.value)

            stats.karma = 17
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            assertEquals(17, viewModel.balance.value)
        }
}

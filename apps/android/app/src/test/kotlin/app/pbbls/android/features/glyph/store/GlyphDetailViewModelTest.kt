package app.pbbls.android.features.glyph.store

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import app.pbbls.android.core.model.BuyGlyphResult
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphGridItem
import app.pbbls.android.testing.FakeGlyphMarketService
import app.pbbls.android.testing.FakePathStatsService
import app.pbbls.android.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The glyph detail entry's balance and purchase record (#940). */
class GlyphDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val item =
        GlyphGridItem(
            glyph = Glyph(id = "g1", strokes = emptyList(), viewBox = "0 0 200 200"),
            price = 10,
            owned = false,
            createdAt = null,
            acquiredAt = null,
        )

    private fun viewModel(
        stats: FakePathStatsService = FakePathStatsService(karma = 42),
        savedState: SavedStateHandle = SavedStateHandle(),
    ) = GlyphDetailViewModel(FakeGlyphMarketService(), stats, savedState)

    @Test
    fun `the balance is the shared karma`() =
        runTest {
            val stats = FakePathStatsService(karma = 42)
            val viewModel = viewModel(stats)
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
            val viewModel = viewModel(stats)
            advanceUntilIdle()

            viewModel.onRecorded(item, BuyGlyphResult(entitlementId = "e1", balance = 32))
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            assertEquals("the new balance must reach the shared stats", 32, stats.karma)
            assertEquals(32, viewModel.balance.value)
        }

    @Test
    fun `a balance written elsewhere reaches the detail`() =
        runTest {
            val stats = FakePathStatsService(karma = null)
            val viewModel = viewModel(stats)
            advanceUntilIdle()
            assertEquals(0, viewModel.balance.value)

            stats.karma = 17
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            assertEquals(17, viewModel.balance.value)
        }

    @Test
    fun `an unbought glyph is shown as the key carries it`() {
        val viewModel = viewModel()

        assertSame(item, viewModel.shown(item))
    }

    /**
     * The key's item still says unowned after a buy. Rebuilding the panel from
     * it (rotation, resize, sheet to pane) used to offer the swap again, and
     * the second buy failed with `already_owned` (#849's failure, #940).
     */
    @Test
    fun `a recorded purchase shows the glyph owned`() {
        val viewModel = viewModel()

        viewModel.onRecorded(item, BuyGlyphResult(entitlementId = "e1", balance = 32))

        val shown = viewModel.shown(item)
        assertTrue(shown.owned)
        assertNotNull(shown.acquiredAt)
    }

    @Test
    fun `the purchase survives the view model being rebuilt`() {
        val savedState = SavedStateHandle()
        viewModel(savedState = savedState).onRecorded(item, BuyGlyphResult(entitlementId = "e1", balance = 32))

        val rebuilt = viewModel(savedState = savedState)

        val shown = rebuilt.shown(item)
        assertTrue(shown.owned)
        assertNotNull(shown.acquiredAt)
    }

    @Test
    fun `a purchase of one glyph does not mark another`() {
        val viewModel = viewModel()
        viewModel.onRecorded(item, BuyGlyphResult(entitlementId = "e1", balance = 32))

        val other = item.copy(glyph = item.glyph.copy(id = "g2"))
        assertFalse(viewModel.shown(other).owned)
    }
}

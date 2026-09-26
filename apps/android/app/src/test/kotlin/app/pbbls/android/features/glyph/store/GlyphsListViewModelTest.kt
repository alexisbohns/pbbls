package app.pbbls.android.features.glyph.store

import app.pbbls.android.R
import app.pbbls.android.core.model.BuyGlyphResult
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphGridItem
import app.pbbls.android.testing.FakeGlyphMarketService
import app.pbbls.android.testing.FakeGlyphService
import app.pbbls.android.testing.FakePathStatsService
import app.pbbls.android.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** The glyph store's tabs, rename and purchase bookkeeping (#849). */
class GlyphsListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun glyph(
        id: String,
        name: String? = null,
        userId: String? = "me",
    ) = Glyph(id = id, name = name, strokes = emptyList(), viewBox = "0 0 200 200", userId = userId)

    private fun item(
        id: String,
        owned: Boolean = false,
        price: Int = 10,
    ) = GlyphGridItem(
        glyph = glyph(id),
        price = price,
        owned = owned,
        createdAt = null,
        acquiredAt = null,
    )

    private fun viewModel(
        market: FakeGlyphMarketService = FakeGlyphMarketService(),
        glyphs: FakeGlyphService = FakeGlyphService(),
        stats: FakePathStatsService = FakePathStatsService(),
    ) = GlyphsListViewModel(market, glyphs, stats)

    // MARK: - Tabs

    @Test
    fun `opens on Mine and resolves to Content`() =
        runTest {
            val market = FakeGlyphMarketService(mine = listOf(item("a")))
            val viewModel = viewModel(market)

            assertEquals(GlyphsUiState.Loading(GlyphTab.MINE), viewModel.uiState.value)
            advanceUntilIdle()

            val state = viewModel.uiState.value as GlyphsUiState.Content
            assertEquals(GlyphTab.MINE, state.tab)
            assertEquals(listOf("a"), state.items.map { it.id })
            assertEquals(1, market.mineCount)
        }

    @Test
    fun `selecting a tab loads it`() =
        runTest {
            val market = FakeGlyphMarketService(community = listOf(item("c")))
            val viewModel = viewModel(market)
            advanceUntilIdle()

            viewModel.onSelectTab(GlyphTab.COMMU)
            advanceUntilIdle()

            val state = viewModel.uiState.value as GlyphsUiState.Content
            assertEquals(GlyphTab.COMMU, state.tab)
            assertEquals(listOf("c"), state.items.map { it.id })
        }

    /**
     * The first visit to a tab has nothing cached, so it passes through
     * Loading. Loading used to carry no tab, and the screen fell back to Mine:
     * the tab bar flashed Mine on every first switch (#855).
     */
    @Test
    fun `loading an uncached tab keeps that tab selected`() =
        runTest {
            val market = FakeGlyphMarketService(owned = listOf(item("o", owned = true)))
            val viewModel = viewModel(market)
            advanceUntilIdle()

            viewModel.onSelectTab(GlyphTab.OWNED)

            assertEquals(GlyphsUiState.Loading(GlyphTab.OWNED), viewModel.uiState.value)
        }

    /**
     * The per-tab cache: coming back to a tab shows it at once and refetches
     * underneath, rather than blanking to a spinner.
     */
    @Test
    fun `returning to a cached tab renders it immediately`() =
        runTest {
            val market = FakeGlyphMarketService(mine = listOf(item("a")), community = listOf(item("c")))
            val viewModel = viewModel(market)
            advanceUntilIdle()

            viewModel.onSelectTab(GlyphTab.COMMU)
            advanceUntilIdle()
            viewModel.onSelectTab(GlyphTab.MINE)

            // Before the refetch has run.
            val state = viewModel.uiState.value as GlyphsUiState.Content
            assertEquals(listOf("a"), state.items.map { it.id })
            assertTrue(state.isLoadingTab)

            advanceUntilIdle()
            assertEquals(2, market.mineCount)
        }

    @Test
    fun `an empty tab is Content, not Error`() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()

            assertTrue((viewModel.uiState.value as GlyphsUiState.Content).items.isEmpty())
        }

    @Test
    fun `a failed load on an empty tab is Error, and coming back recovers`() =
        runTest {
            val market = FakeGlyphMarketService(mine = listOf(item("a")))
            market.failNext = IOException("offline")
            val viewModel = viewModel(market)
            advanceUntilIdle()

            val error = viewModel.uiState.value as GlyphsUiState.Error
            assertEquals(R.string.glyphs_load_error, error.messageRes)
            // The bar must keep highlighting the tab that failed, not snap to Mine.
            assertEquals(GlyphTab.MINE, error.tab)

            // There is no retry button on this branch (unchanged from before the
            // migration), so leaving the tab and returning is the recovery.
            viewModel.onSelectTab(GlyphTab.COMMU)
            advanceUntilIdle()
            viewModel.onSelectTab(GlyphTab.MINE)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is GlyphsUiState.Content)
        }

    /** The failed tab keeps the highlight — the bar must not claim to be on Mine. */
    @Test
    fun `an error carries the tab that failed`() =
        runTest {
            val market = FakeGlyphMarketService(mine = listOf(item("a")))
            val viewModel = viewModel(market)
            advanceUntilIdle()

            market.failNext = IOException("offline")
            viewModel.onSelectTab(GlyphTab.COMMU)
            advanceUntilIdle()

            assertEquals(GlyphTab.COMMU, (viewModel.uiState.value as GlyphsUiState.Error).tab)
        }

    /** A failed refetch over a populated tab keeps the glyphs on screen. */
    @Test
    fun `a failed refetch keeps a populated tab`() =
        runTest {
            val market = FakeGlyphMarketService(mine = listOf(item("a")))
            val viewModel = viewModel(market)
            advanceUntilIdle()

            viewModel.onSelectTab(GlyphTab.COMMU)
            advanceUntilIdle()
            market.failNext = IOException("offline")
            viewModel.onSelectTab(GlyphTab.MINE)
            advanceUntilIdle()

            val state = viewModel.uiState.value as GlyphsUiState.Content
            assertEquals(listOf("a"), state.items.map { it.id })
        }

    // MARK: - Returning from the carve studio

    /**
     * The gap promotion opened (#852): `GlyphCarve` is now a separate entry with no
     * `onCarved` callback back into this instance, so a freshly carved glyph
     * has to be picked up by a resume of the current tab — same mechanism as
     * [SoulsListViewModel.onResumed]. The optimistic prepend-and-switch-to-Mine
     * behaviour #852 removed is NOT restored here: a carve made while on
     * another tab reloads that tab, not Mine.
     */
    @Test
    fun `returning from the carve studio reloads the current tab`() =
        runTest {
            val market = FakeGlyphMarketService(mine = listOf(item("a")))
            val viewModel = viewModel(market)
            advanceUntilIdle()
            assertEquals(1, market.mineCount)

            // First resume: the screen just opened, `init` already loaded Mine.
            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(1, market.mineCount)

            // Second: back from carving a glyph. The fake now returns the carved
            // glyph too, simulating the server-side insert.
            market.mine = listOf(item("a"), item("b"))
            viewModel.onResumed()
            advanceUntilIdle()

            assertEquals(2, market.mineCount)
            val state = viewModel.uiState.value as GlyphsUiState.Content
            assertEquals(listOf("a", "b"), state.items.map { it.id })
        }

    /** A resume while on a different tab reloads that tab, not Mine. */
    @Test
    fun `returning while on another tab reloads that tab, not Mine`() =
        runTest {
            val market = FakeGlyphMarketService(mine = listOf(item("a")), community = listOf(item("c")))
            val viewModel = viewModel(market)
            advanceUntilIdle()
            viewModel.onSelectTab(GlyphTab.COMMU)
            advanceUntilIdle()

            // First resume ever is skipped, whichever tab it lands on.
            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(1, market.mineCount)
            assertEquals(1, market.communityCount)

            // Second: back from carving, while still on Community.
            viewModel.onResumed()
            advanceUntilIdle()

            assertEquals(1, market.mineCount)
            assertEquals(2, market.communityCount)
        }

    // MARK: - Purchase bookkeeping

    /**
     * **What a landed purchase has to change, and why it is all in one place.**
     *
     * `buy_glyph` spends karma, inserts the entitlement and credits the creator.
     * Recording it means three separate things: the new balance, dropping the
     * glyph from Community, and invalidating Owned so its next visit refetches.
     * Before #849 all three lived in an `onSwapped` callback on the host, which
     * a cancelled coroutine skipped — leaving karma spent against a glyph the
     * screen still showed as unowned.
     */
    @Test
    fun `a landed purchase applies the balance and updates both caches`() =
        runTest {
            val market =
                FakeGlyphMarketService(
                    community = listOf(item("c1"), item("c2")),
                    owned = listOf(item("o1", owned = true)),
                )
            val stats = FakePathStatsService(karma = 100)
            val viewModel = viewModel(market, stats = stats)
            advanceUntilIdle()

            // Visit Owned so it is cached, then buy from Community.
            viewModel.onSelectTab(GlyphTab.OWNED)
            advanceUntilIdle()
            assertEquals(1, market.ownedCount)
            viewModel.onSelectTab(GlyphTab.COMMU)
            advanceUntilIdle()

            val target = item("c1")
            viewModel.openDetail(target)
            viewModel.onPurchased(target, BuyGlyphResult(entitlementId = "ent-1", balance = 90))
            advanceUntilIdle()

            assertEquals("the new balance must reach the shared stats", 90, stats.karma)
            val state = viewModel.uiState.value as GlyphsUiState.Content
            assertEquals("the bought glyph leaves Community", listOf("c2"), state.items.map { it.id })

            // Owned was invalidated, so revisiting refetches rather than serving
            // a cache that predates the purchase.
            viewModel.onSelectTab(GlyphTab.OWNED)
            advanceUntilIdle()
            assertEquals(2, market.ownedCount)
        }

    /** The drawer morphs to Owned in place rather than dismissing (M43 D4). */
    @Test
    fun `a landed purchase flips the open drawer to owned`() =
        runTest {
            val market = FakeGlyphMarketService(community = listOf(item("c1")))
            val viewModel = viewModel(market)
            advanceUntilIdle()
            viewModel.onSelectTab(GlyphTab.COMMU)
            advanceUntilIdle()

            val target = item("c1")
            viewModel.openDetail(target)
            assertFalse(
                viewModel.covers.value.selected!!
                    .owned,
            )

            viewModel.onPurchased(target, BuyGlyphResult(entitlementId = "ent-1", balance = 90))

            val selected = viewModel.covers.value.selected
            assertTrue(selected!!.owned)
            assertTrue(selected.acquiredAt != null)
        }

    /**
     * The purchase records from inside the panel's uncancellable section, so it
     * can land after the sheet was swiped away. An unconditional write would
     * slide the drawer back up on its own, showing an Owned state nobody asked
     * to see — and a tap landing there dismisses something else entirely.
     */
    @Test
    fun `a purchase landing after the drawer closed does not reopen it`() =
        runTest {
            val market = FakeGlyphMarketService(community = listOf(item("c1")))
            val viewModel = viewModel(market)
            advanceUntilIdle()
            viewModel.onSelectTab(GlyphTab.COMMU)
            advanceUntilIdle()

            val target = item("c1")
            viewModel.openDetail(target)
            viewModel.closeDetail()

            viewModel.onPurchased(target, BuyGlyphResult(entitlementId = "ent-1", balance = 90))

            assertNull("the sheet is gone — it must stay gone", viewModel.covers.value.selected)
            // The purchase itself is still recorded.
            val state = viewModel.uiState.value as GlyphsUiState.Content
            assertTrue(state.items.isEmpty())
        }

    // MARK: - Rename

    @Test
    fun `rename is optimistic and settles on the server's normalization`() =
        runTest {
            val market = FakeGlyphMarketService(mine = listOf(item("a")))
            val glyphs = FakeGlyphService()
            val viewModel = viewModel(market, glyphs)
            advanceUntilIdle()

            viewModel.requestRename(glyph("a"))
            viewModel.confirmRename("  Anchor  ")

            // Optimistic, before the request runs.
            val optimistic = viewModel.uiState.value as GlyphsUiState.Content
            assertEquals(
                "Anchor",
                optimistic.items
                    .single()
                    .glyph.name,
            )

            advanceUntilIdle()
            assertEquals(listOf("a" to "  Anchor  "), glyphs.updateNameCalls)
            val settled = viewModel.uiState.value as GlyphsUiState.Content
            assertEquals(
                "Anchor",
                settled.items
                    .single()
                    .glyph.name,
            )
        }

    /**
     * The revert is the only thing that undoes the optimism, so losing it would
     * leave a name on screen the server never accepted.
     */
    @Test
    fun `a failed rename restores the original name and reports`() =
        runTest {
            val market = FakeGlyphMarketService(mine = listOf(item("a")))
            val glyphs = FakeGlyphService()
            val viewModel = viewModel(market, glyphs)
            advanceUntilIdle()

            glyphs.failNext = IOException("offline")
            viewModel.requestRename(glyph("a"))
            viewModel.confirmRename("Anchor")
            advanceUntilIdle()

            val restored = viewModel.uiState.value as GlyphsUiState.Content
            assertNull(
                restored.items
                    .single()
                    .glyph.name,
            )
            assertTrue(viewModel.covers.value.didRenameFail)

            viewModel.dismissRenameError()
            assertFalse(viewModel.covers.value.didRenameFail)
        }

    /**
     * The banner had no dismissal path of its own, so without this it stayed up
     * for the life of the screen — including over later renames that worked.
     */
    @Test
    fun `opening the rename dialog clears the previous failure`() =
        runTest {
            val market = FakeGlyphMarketService(mine = listOf(item("a")))
            val glyphs = FakeGlyphService()
            val viewModel = viewModel(market, glyphs)
            advanceUntilIdle()

            glyphs.failNext = IOException("offline")
            viewModel.requestRename(glyph("a"))
            viewModel.confirmRename("Anchor")
            advanceUntilIdle()
            assertTrue(viewModel.covers.value.didRenameFail)

            viewModel.requestRename(glyph("a"))

            assertFalse(viewModel.covers.value.didRenameFail)
        }

    @Test
    fun `cancelling a rename writes nothing`() =
        runTest {
            val glyphs = FakeGlyphService()
            val viewModel = viewModel(FakeGlyphMarketService(mine = listOf(item("a"))), glyphs)
            advanceUntilIdle()

            viewModel.requestRename(glyph("a"))
            viewModel.cancelRename()
            viewModel.confirmRename("Anchor")
            advanceUntilIdle()

            assertTrue(glyphs.updateNameCalls.isEmpty())
        }
}

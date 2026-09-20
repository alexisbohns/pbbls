package app.pbbls.android.features.path.create.pickers

import app.pbbls.android.R
import app.pbbls.android.features.glyph.models.BuyGlyphResult
import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.features.glyph.models.GlyphGridItem
import app.pbbls.android.features.glyph.store.GlyphTab
import app.pbbls.android.testing.FakeGlyphMarketService
import app.pbbls.android.testing.FakePathStatsService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.recordEffects
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * The tabbed glyph picker's load/cache/error bookkeeping and its selection
 * effect (#852) — the ViewModel that replaces `GlyphPickerSheet`'s own
 * `remember`ed `itemsByTab`/`isLoading`/`loadFailed`/`reloadToken`.
 */
class GlyphPickerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun glyph(
        id: String,
        userId: String? = "me",
    ) = Glyph(id = id, name = null, strokes = emptyList(), viewBox = "0 0 200 200", userId = userId)

    private fun item(
        id: String,
        owned: Boolean = false,
        price: Int = 10,
    ) = GlyphGridItem(glyph = glyph(id), price = price, owned = owned, createdAt = null, acquiredAt = null)

    private fun viewModel(
        market: FakeGlyphMarketService = FakeGlyphMarketService(),
        stats: FakePathStatsService = FakePathStatsService(),
    ) = GlyphPickerViewModel(market, stats)

    // MARK: - Load

    @Test
    fun `load populates each tab`() =
        runTest {
            val market =
                FakeGlyphMarketService(
                    mine = listOf(item("m1"), item("m2")),
                    owned = listOf(item("o1", owned = true)),
                    community = listOf(item("c1"), item("c2")),
                )
            val viewModel = viewModel(market)

            assertEquals(GlyphPickerUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()

            val mine = viewModel.uiState.value as GlyphPickerUiState.Content
            assertEquals(GlyphTab.MINE, mine.tab)
            assertEquals(listOf("m1", "m2"), mine.items.map { it.id })

            viewModel.onSelectTab(GlyphTab.OWNED)
            advanceUntilIdle()
            val owned = viewModel.uiState.value as GlyphPickerUiState.Content
            assertEquals(listOf("o1"), owned.items.map { it.id })

            viewModel.onSelectTab(GlyphTab.COMMU)
            advanceUntilIdle()
            val commu = viewModel.uiState.value as GlyphPickerUiState.Content
            assertEquals(listOf("c1", "c2"), commu.items.map { it.id })
        }

    /** Design D10: the picker client-filters `!owned` on top of the server `.neq`. */
    @Test
    fun `an owned community glyph is filtered out client-side`() =
        runTest {
            val market =
                FakeGlyphMarketService(community = listOf(item("c1", owned = false), item("c2", owned = true)))
            val viewModel = viewModel(market)

            viewModel.onSelectTab(GlyphTab.COMMU)
            advanceUntilIdle()

            val state = viewModel.uiState.value as GlyphPickerUiState.Content
            assertEquals(listOf("c1"), state.items.map { it.id })
        }

    @Test
    fun `an empty tab is Content, not Error`() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()

            assertTrue((viewModel.uiState.value as GlyphPickerUiState.Content).items.isEmpty())
        }

    // MARK: - Failure

    @Test
    fun `a failed load surfaces an Error with a string resource, not an empty list`() =
        runTest {
            val market = FakeGlyphMarketService(mine = listOf(item("m1")))
            market.failNext = IOException("offline")
            val viewModel = viewModel(market)
            advanceUntilIdle()

            val error = viewModel.uiState.value as GlyphPickerUiState.Error
            assertEquals(R.string.create_glyph_load_error, error.messageRes)
            assertEquals(GlyphTab.MINE, error.tab)
        }

    /** A failed refetch over a populated tab keeps the glyphs on screen. */
    @Test
    fun `a failed refetch keeps a populated tab`() =
        runTest {
            val market = FakeGlyphMarketService(mine = listOf(item("m1")))
            val viewModel = viewModel(market)
            advanceUntilIdle()

            market.failNext = IOException("offline")
            viewModel.retry()
            advanceUntilIdle()

            val state = viewModel.uiState.value as GlyphPickerUiState.Content
            assertEquals(listOf("m1"), state.items.map { it.id })
        }

    /** The old `reloadToken++` retry, now a plain method. */
    @Test
    fun `retry recovers a failed tab`() =
        runTest {
            val market = FakeGlyphMarketService(mine = listOf(item("m1")))
            market.failNext = IOException("offline")
            val viewModel = viewModel(market)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is GlyphPickerUiState.Error)

            viewModel.retry()
            advanceUntilIdle()

            val state = viewModel.uiState.value as GlyphPickerUiState.Content
            assertEquals(listOf("m1"), state.items.map { it.id })
        }

    // MARK: - Selection

    @Test
    fun `selecting a glyph emits the selection through UiEffects, not state`() =
        runTest {
            val market = FakeGlyphMarketService(mine = listOf(item("m1")))
            val viewModel = viewModel(market)
            advanceUntilIdle()
            val effects = recordEffects(viewModel.effects)

            viewModel.selectGlyph(glyph("m1"))
            advanceUntilIdle()

            assertEquals(listOf(GlyphPickerEffect.Selected(glyph("m1"))), effects.values)
            // The state that renders the grid is untouched by the selection.
            val state = viewModel.uiState.value as GlyphPickerUiState.Content
            assertEquals(listOf("m1"), state.items.map { it.id })
            effects.stop()
        }

    // MARK: - Carve

    /**
     * The picker still hosts the carve studio as an inline content swap (it is
     * not the store's nav entry), so a save has no round trip to reload from —
     * it prepends optimistically and selects, same as before the migration.
     */
    @Test
    fun `a carved glyph is prepended to Mine and selected`() =
        runTest {
            val market = FakeGlyphMarketService(mine = listOf(item("m1")))
            val viewModel = viewModel(market)
            advanceUntilIdle()
            val effects = recordEffects(viewModel.effects)

            viewModel.onCarved(glyph("fresh"))
            advanceUntilIdle()

            val state = viewModel.uiState.value as GlyphPickerUiState.Content
            assertEquals(listOf("fresh", "m1"), state.items.map { it.id })
            assertEquals(listOf(GlyphPickerEffect.Selected(glyph("fresh"))), effects.values)
            effects.stop()
        }

    /** Carving while on another tab must not clobber what that tab is showing. */
    @Test
    fun `a carved glyph does not disturb a different tab on screen`() =
        runTest {
            val market = FakeGlyphMarketService(mine = listOf(item("m1")), owned = listOf(item("o1")))
            val viewModel = viewModel(market)
            advanceUntilIdle()
            viewModel.onSelectTab(GlyphTab.OWNED)
            advanceUntilIdle()

            viewModel.onCarved(glyph("fresh"))
            advanceUntilIdle()

            val state = viewModel.uiState.value as GlyphPickerUiState.Content
            assertEquals(GlyphTab.OWNED, state.tab)
            assertEquals(listOf("o1"), state.items.map { it.id })
        }

    // MARK: - Buy

    @Test
    fun `a landed purchase applies the balance`() =
        runTest {
            val stats = FakePathStatsService(karma = 100)
            val viewModel = viewModel(stats = stats)
            advanceUntilIdle()

            viewModel.onPurchaseRecorded(BuyGlyphResult(entitlementId = "ent-1", balance = 60))
            advanceUntilIdle()

            assertEquals(60, stats.karma)
            val state = viewModel.uiState.value as GlyphPickerUiState.Content
            assertEquals(60, state.karma)
        }
}

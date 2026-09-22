package app.pbbls.android.features.glyph.carve

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.testing.FakeAchievementsService
import app.pbbls.android.testing.FakeGlyphService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.recordEffects
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** The carve surface's drawing, discard and save contract (#849). */
class GlyphCarveViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun stroke(d: String) = GlyphStroke(d = d, width = 6.0)

    private fun viewModel(
        glyphs: FakeGlyphService = FakeGlyphService(),
        achievements: FakeAchievementsService = FakeAchievementsService(),
    ) = GlyphCarveViewModel(glyphs, achievements)

    // MARK: - The canvas

    /**
     * The rotation criterion, and the one that costs the user actual work: the
     * strokes lived in a `remember`, so turning the phone discarded the drawing
     * without even the confirmation this screen otherwise insists on.
     */
    @Test
    fun `strokes survive in the ViewModel`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onStrokesChange(listOf(stroke("M 0,0 L 10,10")))
            repeat(5) { viewModel.uiState.value }

            assertEquals(1, viewModel.uiState.value.strokes.size)
        }

    @Test
    fun `an empty canvas cannot be saved`() =
        runTest {
            val glyphs = FakeGlyphService()
            val viewModel = viewModel(glyphs)

            viewModel.onNameChange("Anchor")
            assertFalse(viewModel.uiState.value.canSave)

            viewModel.save()
            advanceUntilIdle()
            assertTrue(glyphs.createCalls.isEmpty())
        }

    // MARK: - Leaving

    /** Nothing drawn is nothing to lose, so cancel goes straight out. */
    @Test
    fun `cancel on an empty canvas leaves without confirming`() =
        runTest {
            val viewModel = viewModel()
            val effects = recordEffects(viewModel.effects)

            viewModel.onCancelRequested()
            advanceUntilIdle()

            assertEquals(listOf(GlyphCarveEffect.Cancelled), effects.values)
            assertFalse(viewModel.uiState.value.isConfirmingDiscard)
            effects.stop()
        }

    @Test
    fun `cancel over a drawing asks first`() =
        runTest {
            val viewModel = viewModel()
            val effects = recordEffects(viewModel.effects)

            viewModel.onStrokesChange(listOf(stroke("M 0,0 L 10,10")))
            viewModel.onCancelRequested()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isConfirmingDiscard)
            assertTrue("leaving must wait for the answer", effects.values.isEmpty())

            viewModel.confirmDiscard()
            advanceUntilIdle()
            assertEquals(listOf(GlyphCarveEffect.Cancelled), effects.values)
            effects.stop()
        }

    @Test
    fun `keeping the drawing dismisses the prompt and nothing else`() =
        runTest {
            val viewModel = viewModel()
            val effects = recordEffects(viewModel.effects)

            viewModel.onStrokesChange(listOf(stroke("M 0,0 L 10,10")))
            viewModel.onCancelRequested()
            viewModel.dismissDiscard()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isConfirmingDiscard)
            assertEquals(1, viewModel.uiState.value.strokes.size)
            assertTrue(effects.values.isEmpty())
            effects.stop()
        }

    // MARK: - Save

    @Test
    fun `save creates the glyph and fires the achievement check`() =
        runTest {
            val glyphs = FakeGlyphService()
            val achievements = FakeAchievementsService()
            val viewModel = viewModel(glyphs, achievements)
            val effects = recordEffects(viewModel.effects)

            viewModel.onNameChange("Anchor")
            viewModel.onStrokesChange(listOf(stroke("M 0,0 L 10,10")))
            viewModel.save()
            advanceUntilIdle()

            assertEquals(1, glyphs.createCalls.size)
            assertEquals("Anchor", glyphs.createCalls.single().second)
            assertEquals(1, achievements.fireCheckCount)
            assertEquals(1, effects.values.size)
            val saved = effects.values.single()
            assertTrue(saved is GlyphCarveEffect.Saved)
            effects.stop()
        }

    @Test
    fun `a failed save keeps the drawing on screen`() =
        runTest {
            val glyphs = FakeGlyphService()
            val viewModel = viewModel(glyphs)
            val effects = recordEffects(viewModel.effects)

            viewModel.onStrokesChange(listOf(stroke("M 0,0 L 10,10")))
            glyphs.failNext = IOException("offline")
            viewModel.save()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isSaving)
            assertTrue(state.didSaveFail)
            val kept = state.strokes
            assertEquals("the strokes are the user's work — never dropped", 1, kept.size)
            assertTrue(effects.values.isEmpty())
            effects.stop()
        }

    /**
     * **The write this ViewModel exists for.** `create` inserts the glyph and
     * `fireCheck()` follows it, so leaving between the two left a real glyph
     * whose achievement was never evaluated — and nothing re-evaluates it.
     */
    @Test
    fun `clearing the ViewModel mid-save still fires the achievement check`() =
        runTest {
            val glyphs = FakeGlyphService()
            val achievements = FakeAchievementsService()
            val viewModel = viewModel(glyphs, achievements)

            val store = ViewModelStore()
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
                },
            )[GlyphCarveViewModel::class.java]

            val gate = CompletableDeferred<Unit>()
            glyphs.writeGate = gate
            viewModel.onStrokesChange(listOf(stroke("M 0,0 L 10,10")))
            viewModel.save()
            advanceUntilIdle()

            assertEquals(1, glyphs.createCalls.size)
            assertEquals(0, achievements.fireCheckCount)

            store.clear()
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                "the glyph exists, so its achievement must still be evaluated",
                1,
                achievements.fireCheckCount,
            )
        }

    /** A saved canvas must not reappear under the next "+". */
    @Test
    fun `a saved carve leaves a clean canvas`() =
        runTest {
            val viewModel = viewModel()
            val effects = recordEffects(viewModel.effects)

            viewModel.onNameChange("Anchor")
            viewModel.onStrokesChange(listOf(stroke("M 0,0 L 10,10")))
            viewModel.save()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("", state.name)
            assertTrue(state.strokes.isEmpty())
            assertFalse(state.isSaving)
            effects.stop()
        }

    /**
     * `GlyphPickerSheet` closes the carve surface by flipping its own flag — a
     * dismiss gesture, a scrim tap, the record flow stepping back — and never
     * reaches the cancel path. That host lives under the Path route, which never
     * pops, so without an explicit release the strokes would surface under the
     * next carve with a "Discard your glyph?" prompt about someone else's work.
     */
    @Test
    fun `reset clears a carve abandoned by its host`() =
        runTest {
            val viewModel = viewModel()
            val effects = recordEffects(viewModel.effects)

            viewModel.onNameChange("Anchor")
            viewModel.onStrokesChange(listOf(stroke("M 0,0 L 10,10")))

            viewModel.reset()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.strokes.isEmpty())
            assertEquals("", state.name)
            // It is a release, not a cancel: the host already closed the surface.
            assertTrue(effects.values.isEmpty())
            effects.stop()
        }

    /** Idempotent, because the host releases on every close including a save. */
    @Test
    fun `reset after a save is a no-op`() =
        runTest {
            val viewModel = viewModel()
            val effects = recordEffects(viewModel.effects)

            viewModel.onStrokesChange(listOf(stroke("M 0,0 L 10,10")))
            viewModel.save()
            advanceUntilIdle()
            val afterSave = effects.values.size

            viewModel.reset()
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.strokes
                    .isEmpty(),
            )
            assertEquals(afterSave, effects.values.size)
            effects.stop()
        }

    @Test
    fun `a discarded carve leaves a clean canvas`() =
        runTest {
            val viewModel = viewModel()
            val effects = recordEffects(viewModel.effects)

            viewModel.onStrokesChange(listOf(stroke("M 0,0 L 10,10")))
            viewModel.onCancelRequested()
            viewModel.confirmDiscard()
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.strokes
                    .isEmpty(),
            )
            effects.stop()
        }
}

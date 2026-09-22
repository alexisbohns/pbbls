package app.pbbls.android.features.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.pbbls.android.R
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.model.SystemGlyph
import app.pbbls.android.testing.FakeAchievementsService
import app.pbbls.android.testing.FakeReferenceDataService
import app.pbbls.android.testing.FakeSoulsService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.recordEffects
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * The soul form's seed, save and reset contract (#849, #852).
 *
 * The reset half is the load-bearing one: this ViewModel is hosted behind a
 * conditionally-composed cover, so `hiltViewModel()` scopes it to the back
 * stack entry underneath, which outlives the cover many times over.
 *
 * Since #852, [SoulFormViewModel.start] takes an id instead of the whole
 * row and fetches it itself — [FakeSoulsService.loadSoul] stands in for the
 * server round trip, so a load is asynchronous and needs `advanceUntilIdle()`
 * before the result lands in [SoulFormViewModel.uiState].
 */
class SoulFormViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun glyph(id: String) = Glyph(id = id, strokes = emptyList(), viewBox = "0 0 200 200")

    private fun soul(
        id: String,
        name: String = "Molly",
        glyphId: String = "glyph-$id",
    ) = SoulWithGlyph(id = id, name = name, glyphId = glyphId, glyph = glyph(glyphId))

    private fun viewModel(
        souls: FakeSoulsService = FakeSoulsService(glyph = glyph(SystemGlyph.DEFAULT)),
        refs: FakeReferenceDataService = FakeReferenceDataService(),
        achievements: FakeAchievementsService = FakeAchievementsService(),
        savedState: SavedStateHandle = SavedStateHandle(),
    ) = SoulFormViewModel(savedState, souls, refs, achievements)

    // MARK: - Seeding by id

    @Test
    fun `a non-null id loads that soul into the form`() =
        runTest {
            val service = FakeSoulsService(glyph = glyph(SystemGlyph.DEFAULT))
            service.soul = soul("s1", name = "Otis", glyphId = "glyph-s1")

            // No `start()` call: `init` reads the id straight off the handle,
            // exactly as a real nav entry supplies one (#852).
            val viewModel = viewModel(souls = service, savedState = SavedStateHandle(mapOf(SOUL_FORM_ID_KEY to "s1")))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Otis", state.name)
            assertEquals("glyph-s1", state.glyphId)
            assertEquals("glyph-s1", state.glyph?.id)
            assertTrue(state.isEditing)
            assertEquals(R.string.soul_edit_title, state.titleRes)
            assertEquals(1, service.loadSoulCount)
        }

    @Test
    fun `a null id opens an empty create form and does not hit the service`() =
        runTest {
            val service = FakeSoulsService(glyph = glyph(SystemGlyph.DEFAULT))
            val viewModel = viewModel(souls = service, savedState = SavedStateHandle())
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("", state.name)
            assertFalse(state.isEditing)
            assertEquals(0, service.loadSoulCount)
        }

    @Test
    fun `a failed load surfaces an error, not an empty form`() =
        runTest {
            val service = FakeSoulsService(glyph = glyph(SystemGlyph.DEFAULT))
            service.failNext = IOException("offline")
            val viewModel = viewModel(souls = service, savedState = SavedStateHandle(mapOf(SOUL_FORM_ID_KEY to "s1")))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertEquals(R.string.soul_form_load_error, state.loadErrorRes)
            assertFalse(state.canSave)
            assertNull(state.original)
        }

    // MARK: - Seeding via `start`

    @Test
    fun `create seeds empty and fetches the default glyph`() =
        runTest {
            val service = FakeSoulsService(glyph = glyph(SystemGlyph.DEFAULT))
            val viewModel = viewModel(souls = service)

            viewModel.start(null)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("", state.name)
            assertEquals(SystemGlyph.DEFAULT, state.glyphId)
            assertEquals(SystemGlyph.DEFAULT, state.glyph?.id)
            assertFalse(state.isEditing)
            assertEquals(R.string.create_soul_title, state.titleRes)
            assertEquals(listOf(SystemGlyph.DEFAULT), service.loadGlyphCalls)
        }

    @Test
    fun `edit seeds from the loaded row and skips the default-glyph fetch`() =
        runTest {
            val service = FakeSoulsService(glyph = glyph(SystemGlyph.DEFAULT))
            service.soul = soul("a")
            val viewModel = viewModel(souls = service)

            viewModel.start("a")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Molly", state.name)
            assertEquals("glyph-a", state.glyphId)
            assertTrue(state.isEditing)
            assertEquals(R.string.soul_edit_title, state.titleRes)
            assertTrue(service.loadGlyphCalls.isEmpty())
        }

    /**
     * The rotation guard: the screen's `LaunchedEffect` re-runs on every
     * configuration change, and must not throw away work in progress.
     *
     * It asserts the picked *glyph* as well as the name, deliberately. Nothing
     * is persisted, so a test that checked only the name would pass with the
     * guard deleted — while a real rotation silently reset the glyph.
     */
    @Test
    fun `start does not re-seed over the user's edits`() =
        runTest {
            val service = FakeSoulsService(glyph = glyph(SystemGlyph.DEFAULT))
            service.soul = soul("a")
            val viewModel = viewModel(souls = service)
            viewModel.start("a")
            advanceUntilIdle()

            viewModel.onNameChange("Maude")
            viewModel.onGlyphPicked(glyph("picked"))

            viewModel.start("a")

            val state = viewModel.uiState.value
            assertEquals("Maude", state.name)
            assertEquals("picked", state.glyphId)
            assertEquals("picked", state.glyph?.id)
        }

    /** …but opening the form on a different soul starts clean. */
    @Test
    fun `start on a different soul re-seeds`() =
        runTest {
            val service = FakeSoulsService(glyph = glyph(SystemGlyph.DEFAULT))
            service.soul = soul("a", name = "Molly")
            val viewModel = viewModel(souls = service)
            viewModel.start("a")
            advanceUntilIdle()
            viewModel.onNameChange("Maude")
            viewModel.onGlyphPicked(glyph("picked"))

            service.soul = soul("b", name = "Otis", glyphId = "glyph-b")
            viewModel.start("b")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Otis", state.name)
            assertEquals("glyph-b", state.glyphId)
        }

    /**
     * A picker selection made while the default-glyph fetch is in flight must
     * not be clobbered by the late default.
     */
    @Test
    fun `a late default glyph does not overwrite a picked one`() =
        runTest {
            val service = FakeSoulsService(glyph = glyph(SystemGlyph.DEFAULT))
            service.loadGlyphGate = CompletableDeferred()
            val viewModel = viewModel(souls = service)

            viewModel.start(null)
            advanceUntilIdle()
            viewModel.onGlyphPicked(glyph("picked"))

            service.loadGlyphGate?.complete(Unit)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("picked", state.glyph?.id)
            assertEquals("picked", state.glyphId)
        }

    @Test
    fun `a failed default-glyph fetch leaves the placeholder`() =
        runTest {
            val service = FakeSoulsService(glyph = glyph(SystemGlyph.DEFAULT))
            service.failNext = IOException("offline")
            val viewModel = viewModel(souls = service)

            viewModel.start(null)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.glyph)
        }

    // MARK: - Save

    @Test
    fun `create writes, refreshes the picker cache, fires the check and emits Saved`() =
        runTest {
            val service = FakeSoulsService(glyph = glyph(SystemGlyph.DEFAULT))
            val refs = FakeReferenceDataService()
            val achievements = FakeAchievementsService()
            val viewModel = viewModel(souls = service, refs = refs, achievements = achievements)
            val effects = recordEffects(viewModel.effects)

            viewModel.start(null)
            advanceUntilIdle()
            viewModel.onNameChange("  Molly  ")
            viewModel.save()
            advanceUntilIdle()

            assertEquals(listOf("Molly" to SystemGlyph.DEFAULT), service.createCalls)
            assertEquals(1, refs.refreshSoulsCount)
            assertEquals(1, achievements.fireCheckCount)
            assertEquals(listOf(SoulFormEffect.Saved), effects.values)
            effects.stop()
        }

    @Test
    fun `edit updates rather than creating`() =
        runTest {
            val service = FakeSoulsService(glyph = glyph(SystemGlyph.DEFAULT))
            service.soul = soul("a")
            val achievements = FakeAchievementsService()
            val viewModel = viewModel(souls = service, achievements = achievements)

            viewModel.start("a")
            advanceUntilIdle()
            viewModel.onNameChange("Maude")
            viewModel.save()
            advanceUntilIdle()

            assertEquals(listOf(Triple("a", "Maude", "glyph-a")), service.updateCalls)
            assertTrue(service.createCalls.isEmpty())
            // Editing an existing soul is not a new one, so no achievement fires.
            assertEquals(0, achievements.fireCheckCount)
        }

    /**
     * **The regression this ViewModel exists for.**
     *
     * `save()` ran in `rememberCoroutineScope`, so leaving the cover after the
     * insert had left the device dropped everything that follows it — the
     * achievement check and, worse, the refresh of the cache the composer's soul
     * picker reads. The soul existed on the server and could not be tagged onto
     * a pebble for the rest of the session.
     *
     * Clearing the `ViewModelStore` is the closest a unit test gets to that: it
     * calls `onCleared`, which cancels `viewModelScope`. It is reachable in the
     * app because the cover's `BackHandler` is *disabled* while saving, so a
     * back press during the spinner falls through and pops the host destination.
     */
    @Test
    fun `clearing the ViewModel mid-save still refreshes the picker cache`() =
        runTest {
            val service = FakeSoulsService(glyph = glyph(SystemGlyph.DEFAULT))
            service.writeGate = CompletableDeferred()
            val refs = FakeReferenceDataService()
            val achievements = FakeAchievementsService()
            val viewModel = viewModel(souls = service, refs = refs, achievements = achievements)

            val store = ViewModelStore()
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
                },
            )[SoulFormViewModel::class.java]

            viewModel.start(null)
            advanceUntilIdle()
            viewModel.onNameChange("Molly")
            viewModel.save()
            advanceUntilIdle()

            // In flight at the insert: the request has left the device.
            assertEquals(1, service.createCalls.size)
            assertEquals(0, refs.refreshSoulsCount)

            // The user backs out. Before #849 the save ended right here.
            store.clear()
            service.writeGate?.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                "the picker cache must still be refreshed, or the new soul is untaggable",
                1,
                refs.refreshSoulsCount,
            )
            assertEquals(1, achievements.fireCheckCount)
        }

    @Test
    fun `a failed save stays open with the error`() =
        runTest {
            val service = FakeSoulsService(glyph = glyph(SystemGlyph.DEFAULT))
            val viewModel = viewModel(souls = service)
            val effects = recordEffects(viewModel.effects)

            viewModel.start(null)
            advanceUntilIdle()
            viewModel.onNameChange("Molly")
            service.failNext = IOException("offline")
            viewModel.save()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isSaving)
            assertTrue(state.didSaveFail)
            assertEquals("Molly", state.name)
            assertTrue(effects.values.isEmpty())
            effects.stop()
        }

    @Test
    fun `save is refused while the form cannot save`() =
        runTest {
            val service = FakeSoulsService(glyph = glyph(SystemGlyph.DEFAULT))
            val viewModel = viewModel(souls = service)

            viewModel.start(null)
            advanceUntilIdle()
            viewModel.onNameChange("   ")
            viewModel.save()
            advanceUntilIdle()

            assertTrue(service.createCalls.isEmpty())
        }

    /** Cancel is inert while a save is in flight — the top bar hides it too. */
    @Test
    fun `dismiss is refused while saving`() =
        runTest {
            val service = FakeSoulsService(glyph = glyph(SystemGlyph.DEFAULT))
            service.writeGate = CompletableDeferred()
            val viewModel = viewModel(souls = service)
            val effects = recordEffects(viewModel.effects)

            viewModel.start(null)
            advanceUntilIdle()
            viewModel.onNameChange("Molly")
            viewModel.save()
            advanceUntilIdle()

            viewModel.onDismissRequested()
            advanceUntilIdle()
            assertTrue(effects.values.isEmpty())

            service.writeGate?.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf(SoulFormEffect.Saved), effects.values)
            effects.stop()
        }

    // MARK: - Reset

    /**
     * Without [SoulFormViewModel]'s reset the next "+" opens onto the soul just
     * saved — the ViewModel outlives the cover.
     *
     * Asserted after the re-`start`, which is where the user would see it: the
     * reset deliberately leaves the visible state alone, so the cover does not
     * repaint as a blank create form on its way out.
     */
    @Test
    fun `a saved form is clean for the next presentation`() =
        runTest {
            val service = FakeSoulsService(glyph = glyph(SystemGlyph.DEFAULT))
            val viewModel = viewModel(souls = service)

            viewModel.start(null)
            advanceUntilIdle()
            viewModel.onNameChange("Molly")
            viewModel.save()
            advanceUntilIdle()

            viewModel.start(null)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("", state.name)
            assertFalse(state.isSaving)
            assertFalse(state.isEditing)
        }

    @Test
    fun `a dismissed form is clean for the next presentation`() =
        runTest {
            val viewModel = viewModel()
            val effects = recordEffects(viewModel.effects)

            viewModel.start(null)
            advanceUntilIdle()
            viewModel.onNameChange("Molly")
            viewModel.onDismissRequested()
            advanceUntilIdle()

            assertEquals(listOf(SoulFormEffect.Dismiss), effects.values)

            viewModel.start(null)
            advanceUntilIdle()
            assertEquals("", viewModel.uiState.value.name)
            effects.stop()
        }

    /**
     * Re-opening on the soul that was just *edited* must re-seed too — the guard
     * keys on the id, so without releasing it the form would skip hydration and
     * keep showing the pre-save row for the rest of the session.
     */
    @Test
    fun `an edited soul re-seeds from the saved row on re-open`() =
        runTest {
            val service = FakeSoulsService(glyph = glyph(SystemGlyph.DEFAULT))
            service.soul = soul("a", name = "Molly")
            val viewModel = viewModel(souls = service)

            viewModel.start("a")
            advanceUntilIdle()
            viewModel.onNameChange("Maude")
            viewModel.save()
            advanceUntilIdle()

            service.soul = soul("a", name = "Maude")
            viewModel.start("a")
            advanceUntilIdle()

            assertEquals("Maude", viewModel.uiState.value.name)
        }
}

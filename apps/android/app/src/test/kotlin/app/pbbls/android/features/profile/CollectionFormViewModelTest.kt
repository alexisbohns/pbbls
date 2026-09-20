package app.pbbls.android.features.profile

import app.pbbls.android.R
import app.pbbls.android.features.profile.models.Collection
import app.pbbls.android.features.profile.models.CollectionMode
import app.pbbls.android.testing.FakeAchievementsService
import app.pbbls.android.testing.FakeCollectionsService
import app.pbbls.android.testing.FakeReferenceDataService
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

/** The collection form's seed, save and reset contract (#849). */
class CollectionFormViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun collection(
        id: String,
        name: String = "Trips",
        mode: CollectionMode? = CollectionMode.STACK,
    ) = Collection(id = id, name = name, mode = mode, pebbleCount = 0)

    private fun viewModel(
        collections: FakeCollectionsService = FakeCollectionsService(),
        refs: FakeReferenceDataService = FakeReferenceDataService(),
        achievements: FakeAchievementsService = FakeAchievementsService(),
    ) = CollectionFormViewModel(collections, refs, achievements)

    // MARK: - Seeding

    @Test
    fun `create seeds empty`() =
        runTest {
            val viewModel = viewModel()
            viewModel.start(null)

            val state = viewModel.uiState.value
            assertEquals("", state.name)
            assertNull(state.mode)
            assertFalse(state.isEditing)
            assertEquals(R.string.profile_collection_new, state.titleRes)
        }

    @Test
    fun `edit seeds from the row`() =
        runTest {
            val viewModel = viewModel()
            viewModel.start(collection("a"))

            val state = viewModel.uiState.value
            assertEquals("Trips", state.name)
            assertEquals(CollectionMode.STACK, state.mode)
            assertTrue(state.isEditing)
            assertEquals(R.string.collection_edit_title, state.titleRes)
        }

    /**
     * The rotation guard. It asserts the *mode* as well as the name: nothing is
     * persisted, so a name-only assertion would pass with the guard deleted
     * while a real rotation silently reset the picker.
     */
    @Test
    fun `start does not re-seed over the user's edits`() =
        runTest {
            val viewModel = viewModel()
            viewModel.start(collection("a"))
            viewModel.onNameChange("Journeys")
            viewModel.onModeChange(CollectionMode.PACK)

            viewModel.start(collection("a"))

            val state = viewModel.uiState.value
            assertEquals("Journeys", state.name)
            assertEquals(CollectionMode.PACK, state.mode)
        }

    @Test
    fun `start on a different collection re-seeds`() =
        runTest {
            val viewModel = viewModel()
            viewModel.start(collection("a", name = "Trips"))
            viewModel.onNameChange("Journeys")
            viewModel.onModeChange(CollectionMode.PACK)

            viewModel.start(collection("b", name = "Moods", mode = CollectionMode.TRACK))

            val state = viewModel.uiState.value
            assertEquals("Moods", state.name)
            assertEquals(CollectionMode.TRACK, state.mode)
        }

    // MARK: - Save

    @Test
    fun `create writes, refreshes the picker cache, fires the check and emits Saved`() =
        runTest {
            val service = FakeCollectionsService()
            val refs = FakeReferenceDataService()
            val achievements = FakeAchievementsService()
            val viewModel = viewModel(collections = service, refs = refs, achievements = achievements)
            val effects = recordEffects(viewModel.effects)

            viewModel.start(null)
            viewModel.onNameChange("  Trips  ")
            viewModel.onModeChange(CollectionMode.TRACK)
            viewModel.save()
            advanceUntilIdle()

            assertEquals(listOf("Trips" to CollectionMode.TRACK), service.createCalls)
            assertEquals(1, refs.refreshCollectionsCount)
            assertEquals(1, achievements.fireCheckCount)
            assertEquals(listOf(CollectionFormEffect.Saved), effects.values)
            effects.stop()
        }

    /** Clearing the mode on edit is a real change, and the payload sends null. */
    @Test
    fun `edit can clear the mode`() =
        runTest {
            val service = FakeCollectionsService()
            val achievements = FakeAchievementsService()
            val viewModel = viewModel(collections = service, achievements = achievements)

            viewModel.start(collection("a"))
            viewModel.onModeChange(null)
            assertTrue(viewModel.uiState.value.canSave)

            viewModel.save()
            advanceUntilIdle()

            assertEquals(listOf(Triple("a", "Trips", null)), service.updateCalls)
            assertTrue(service.createCalls.isEmpty())
            assertEquals(0, achievements.fireCheckCount)
        }

    @Test
    fun `a failed save stays open with the error`() =
        runTest {
            val service = FakeCollectionsService()
            val viewModel = viewModel(collections = service)
            val effects = recordEffects(viewModel.effects)

            viewModel.start(null)
            viewModel.onNameChange("Trips")
            service.failNext = IOException("offline")
            viewModel.save()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isSaving)
            assertTrue(state.didSaveFail)
            assertEquals("Trips", state.name)
            assertTrue(effects.values.isEmpty())
            effects.stop()
        }

    @Test
    fun `save is refused while the form cannot save`() =
        runTest {
            val service = FakeCollectionsService()
            val viewModel = viewModel(collections = service)

            viewModel.start(null)
            viewModel.onNameChange("   ")
            viewModel.save()
            advanceUntilIdle()

            assertTrue(service.createCalls.isEmpty())
        }

    @Test
    fun `dismiss is refused while saving`() =
        runTest {
            val service = FakeCollectionsService()
            service.writeGate = CompletableDeferred()
            val viewModel = viewModel(collections = service)
            val effects = recordEffects(viewModel.effects)

            viewModel.start(null)
            viewModel.onNameChange("Trips")
            viewModel.save()
            advanceUntilIdle()

            viewModel.onDismissRequested()
            advanceUntilIdle()
            assertTrue(effects.values.isEmpty())

            service.writeGate?.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf(CollectionFormEffect.Saved), effects.values)
            effects.stop()
        }

    // MARK: - Reset

    /**
     * Profile, the collections list and the collection detail all host this
     * cover, so the reset is what keeps "+" from opening onto the collection
     * just saved. Asserted after the re-`start` — see the souls twin for why the
     * reset leaves the visible state alone.
     */
    @Test
    fun `a saved form is clean for the next presentation`() =
        runTest {
            val viewModel = viewModel()

            viewModel.start(null)
            viewModel.onNameChange("Trips")
            viewModel.onModeChange(CollectionMode.PACK)
            viewModel.save()
            advanceUntilIdle()

            viewModel.start(null)

            val state = viewModel.uiState.value
            assertEquals("", state.name)
            assertNull(state.mode)
            assertFalse(state.isEditing)
        }

    @Test
    fun `a dismissed form is clean for the next presentation`() =
        runTest {
            val viewModel = viewModel()
            val effects = recordEffects(viewModel.effects)

            viewModel.start(null)
            viewModel.onNameChange("Trips")
            viewModel.onDismissRequested()
            advanceUntilIdle()

            assertEquals(listOf(CollectionFormEffect.Dismiss), effects.values)

            viewModel.start(null)
            assertEquals("", viewModel.uiState.value.name)
            effects.stop()
        }
}

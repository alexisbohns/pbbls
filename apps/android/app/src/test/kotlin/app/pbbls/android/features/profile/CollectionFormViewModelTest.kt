package app.pbbls.android.features.profile

import androidx.lifecycle.SavedStateHandle
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

/**
 * The collection form's seed, save and reset contract (#849, #852).
 *
 * Since #852, [CollectionFormViewModel.start] takes an id instead of the
 * whole row and fetches it itself — [FakeCollectionsService.loadCollection]
 * stands in for the server round trip, so a load is asynchronous and needs
 * `advanceUntilIdle()` before the result lands in
 * [CollectionFormViewModel.uiState]. See [SoulFormViewModelTest] for the twin
 * suite this mirrors.
 */
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
        savedState: SavedStateHandle = SavedStateHandle(),
    ) = CollectionFormViewModel(savedState, collections, refs, achievements)

    // MARK: - Seeding by id

    @Test
    fun `a non-null id loads that collection into the form`() =
        runTest {
            val service = FakeCollectionsService()
            service.collection = collection("c1", name = "Journeys", mode = CollectionMode.TRACK)

            // No `start()` call: `init` reads the id straight off the handle,
            // exactly as a real nav entry supplies one (#852).
            val viewModel =
                viewModel(collections = service, savedState = SavedStateHandle(mapOf(COLLECTION_FORM_ID_KEY to "c1")))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Journeys", state.name)
            assertEquals(CollectionMode.TRACK, state.mode)
            assertTrue(state.isEditing)
            assertEquals(R.string.collection_edit_title, state.titleRes)
            assertEquals(1, service.loadCollectionCount)
        }

    @Test
    fun `a null id opens an empty create form and does not hit the service`() =
        runTest {
            val service = FakeCollectionsService()
            val viewModel = viewModel(collections = service, savedState = SavedStateHandle())
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("", state.name)
            assertNull(state.mode)
            assertFalse(state.isEditing)
            assertEquals(0, service.loadCollectionCount)
        }

    @Test
    fun `a failed load surfaces an error, not an empty form`() =
        runTest {
            val service = FakeCollectionsService()
            service.failNext = IOException("offline")
            val viewModel =
                viewModel(collections = service, savedState = SavedStateHandle(mapOf(COLLECTION_FORM_ID_KEY to "c1")))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertEquals(R.string.collection_form_load_error, state.loadErrorRes)
            assertFalse(state.canSave)
            assertNull(state.original)
        }

    // MARK: - Seeding via `start`

    @Test
    fun `create seeds empty`() =
        runTest {
            val viewModel = viewModel()
            viewModel.start(null)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("", state.name)
            assertNull(state.mode)
            assertFalse(state.isEditing)
            assertEquals(R.string.profile_collection_new, state.titleRes)
        }

    @Test
    fun `edit seeds from the loaded row`() =
        runTest {
            val service = FakeCollectionsService()
            service.collection = collection("a")
            val viewModel = viewModel(collections = service)

            viewModel.start("a")
            advanceUntilIdle()

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
            val service = FakeCollectionsService()
            service.collection = collection("a")
            val viewModel = viewModel(collections = service)
            viewModel.start("a")
            advanceUntilIdle()
            viewModel.onNameChange("Journeys")
            viewModel.onModeChange(CollectionMode.PACK)

            viewModel.start("a")

            val state = viewModel.uiState.value
            assertEquals("Journeys", state.name)
            assertEquals(CollectionMode.PACK, state.mode)
        }

    @Test
    fun `start on a different collection re-seeds`() =
        runTest {
            val service = FakeCollectionsService()
            service.collection = collection("a", name = "Trips")
            val viewModel = viewModel(collections = service)
            viewModel.start("a")
            advanceUntilIdle()
            viewModel.onNameChange("Journeys")
            viewModel.onModeChange(CollectionMode.PACK)

            service.collection = collection("b", name = "Moods", mode = CollectionMode.TRACK)
            viewModel.start("b")
            advanceUntilIdle()

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
            advanceUntilIdle()
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
            service.collection = collection("a")
            val achievements = FakeAchievementsService()
            val viewModel = viewModel(collections = service, achievements = achievements)

            viewModel.start("a")
            advanceUntilIdle()
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
            advanceUntilIdle()
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
            advanceUntilIdle()
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
            advanceUntilIdle()
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
            advanceUntilIdle()
            viewModel.onNameChange("Trips")
            viewModel.onModeChange(CollectionMode.PACK)
            viewModel.save()
            advanceUntilIdle()

            viewModel.start(null)
            advanceUntilIdle()

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
            advanceUntilIdle()
            viewModel.onNameChange("Trips")
            viewModel.onDismissRequested()
            advanceUntilIdle()

            assertEquals(listOf(CollectionFormEffect.Dismiss), effects.values)

            viewModel.start(null)
            advanceUntilIdle()
            assertEquals("", viewModel.uiState.value.name)
            effects.stop()
        }
}

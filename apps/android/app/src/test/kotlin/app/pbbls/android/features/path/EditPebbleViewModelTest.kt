package app.pbbls.android.features.path

import app.pbbls.android.R
import app.pbbls.android.core.data.KarmaNotificationService
import app.pbbls.android.core.model.KarmaReason
import app.pbbls.android.core.model.PebbleCompletePayload
import app.pbbls.android.core.model.Visibility
import app.pbbls.android.testing.FakeAchievementsService
import app.pbbls.android.testing.FakeMomentReadService
import app.pbbls.android.testing.FakeMomentWriteService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.UiFixtures
import app.pbbls.android.testing.postgrestException
import app.pbbls.android.testing.recordEffects
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Edit pebble (R5): the pebble half of a moment, written with `complete_pebble` and nothing else. */
class EditPebbleViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class Harness(
        scope: CoroutineScope,
        val read: FakeMomentReadService = FakeMomentReadService(),
        val writes: FakeMomentWriteService = FakeMomentWriteService(),
        val achievements: FakeAchievementsService = FakeAchievementsService(),
    ) {
        val karma = KarmaNotificationService(scope)
        val viewModel = EditPebbleViewModel(moments = read, writes = writes, karma = karma, achievements = achievements)
    }

    @Test
    fun `loads the pebble into the form`() =
        runTest {
            val h = Harness(backgroundScope)
            h.read.moments = mapOf("moment-1" to UiFixtures.momentDetail())

            h.viewModel.start("moment-1")
            assertEquals(EditPebbleUiState.Loading, h.viewModel.uiState.value)
            advanceUntilIdle()

            val draft = (h.viewModel.uiState.value as EditPebbleUiState.Content).draft
            assertEquals(UiFixtures.joy.id, draft.emotionId)
            assertEquals("domain-1", draft.domainId)
        }

    @Test
    fun `saving writes the pebble only, reads the enrichment karma and checks achievements once`() =
        runTest {
            val h = Harness(backgroundScope)
            h.read.moments = mapOf("moment-1" to UiFixtures.momentDetail())
            val effects = recordEffects(h.viewModel.effects)
            h.viewModel.start("moment-1")
            advanceUntilIdle()
            val state = h.viewModel.uiState.value as EditPebbleUiState.Content
            h.viewModel.onDraftChange(state.draft.copy(visibility = Visibility.PUBLIC))
            h.viewModel.save()
            advanceUntilIdle()
            assertEquals(
                PebbleCompletePayload("moment-1", UiFixtures.joy.id, 2, 1, Visibility.PUBLIC, listOf("domain-1"), emptyList(), emptyList()),
                h.writes.completed.single(),
            )
            assertTrue(h.writes.saved.isEmpty())
            assertEquals(listOf("pebble-new" to KarmaReason.PEBBLE_ENRICHED), h.writes.karmaReads)
            assertEquals(1, h.achievements.fireCheckCount)
            assertTrue(effects.values.contains(EditPebbleEffect.Saved))
            effects.stop()
        }

    @Test
    fun `a moment without your pebble has no pebble to edit`() =
        runTest {
            val h = Harness(backgroundScope)
            h.read.moments = mapOf("moment-1" to UiFixtures.momentDetail(pebble = null))
            h.viewModel.start("moment-1")
            advanceUntilIdle()
            assertEquals(EditPebbleUiState.Error, h.viewModel.uiState.value)
        }

    @Test
    fun `a refusal shows the shared copy and checks nothing`() =
        runTest {
            val h = Harness(backgroundScope)
            h.read.moments = mapOf("moment-1" to UiFixtures.momentDetail())
            h.viewModel.start("moment-1")
            advanceUntilIdle()
            h.writes.failOnComplete = postgrestException("soul_not_owned", sqlState = "42501", status = HttpStatusCode.Forbidden)
            h.viewModel.save()
            advanceUntilIdle()
            val state = h.viewModel.uiState.value as EditPebbleUiState.Content
            assertEquals(R.string.pebble_error_soul_gone, state.saveErrorRes)
            assertFalse(state.isSaving)
            assertEquals(0, h.achievements.fireCheckCount)
            assertTrue(h.writes.karmaReads.isEmpty())
        }

    @Test
    fun `an incomplete pebble cannot be saved`() =
        runTest {
            val h = Harness(backgroundScope)
            h.read.moments = mapOf("moment-1" to UiFixtures.momentDetail())
            h.viewModel.start("moment-1")
            advanceUntilIdle()
            val state = h.viewModel.uiState.value as EditPebbleUiState.Content
            h.viewModel.onDraftChange(state.draft.copy(domainId = null))

            assertFalse((h.viewModel.uiState.value as EditPebbleUiState.Content).canSave)
            h.viewModel.save()
            advanceUntilIdle()
            assertTrue(h.writes.completed.isEmpty())
        }

    /** A rotation re-runs the screen's `LaunchedEffect`; it must not discard edits. */
    @Test
    fun `reopening the same moment does not reload over edits in progress`() =
        runTest {
            val h = Harness(backgroundScope)
            h.read.moments = mapOf("moment-1" to UiFixtures.momentDetail())
            h.viewModel.start("moment-1")
            advanceUntilIdle()
            val edited = (h.viewModel.uiState.value as EditPebbleUiState.Content).draft.copy(visibility = Visibility.PRIVATE)
            h.viewModel.onDraftChange(edited)

            h.viewModel.start("moment-1")
            advanceUntilIdle()

            assertEquals(listOf("moment-1"), h.read.loadedMomentIds)
            assertEquals(Visibility.PRIVATE, (h.viewModel.uiState.value as EditPebbleUiState.Content).draft.visibility)
        }

    @Test
    fun `a failed load is the error state and retry recovers`() =
        runTest {
            val h = Harness(backgroundScope)
            h.read.moments = mapOf("moment-1" to UiFixtures.momentDetail())
            h.read.failNext = IOException("offline")

            h.viewModel.start("moment-1")
            advanceUntilIdle()
            assertEquals(EditPebbleUiState.Error, h.viewModel.uiState.value)

            h.viewModel.retry()
            advanceUntilIdle()

            assertTrue(h.viewModel.uiState.value is EditPebbleUiState.Content)
        }

    @Test
    fun `dismissing reports it and writes nothing`() =
        runTest {
            val h = Harness(backgroundScope)
            h.read.moments = mapOf("moment-1" to UiFixtures.momentDetail())
            val effects = recordEffects(h.viewModel.effects)

            h.viewModel.start("moment-1")
            advanceUntilIdle()
            h.viewModel.dismiss()
            advanceUntilIdle()

            assertTrue(effects.values.contains(EditPebbleEffect.Dismissed))
            assertTrue(h.writes.completed.isEmpty())
            effects.stop()
        }
}

package app.pbbls.android.features.path

import app.pbbls.android.R
import app.pbbls.android.features.karma.KarmaNotificationService
import app.pbbls.android.features.path.models.ComposePebbleResponse
import app.pbbls.android.features.path.models.EmotionRef
import app.pbbls.android.features.path.models.PebbleDetail
import app.pbbls.android.features.path.models.Visibility
import app.pbbls.android.services.ComposeResult
import app.pbbls.android.testing.FakeAchievementsService
import app.pbbls.android.testing.FakeComposerMedia
import app.pbbls.android.testing.FakePebbleDetailService
import app.pbbls.android.testing.FakePebbleWriteService
import app.pbbls.android.testing.FakeSnapWriteRepository
import app.pbbls.android.testing.FakeSupabaseService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.recordEffects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.OffsetDateTime

/** The edit cover's load, save and photo paths (#849). */
class EditPebbleViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun detail(
        id: String = "pebble-1",
        name: String = "A walk",
    ) = PebbleDetail(
        id = id,
        name = name,
        happenedAt = OffsetDateTime.parse("2026-09-19T12:00:00Z"),
        intensity = 2,
        positiveness = 1,
        visibility = Visibility.SECRET,
        emotion = EmotionRef(id = "emotion-1", slug = "calm", name = "Calm"),
    )

    private class Harness(
        scope: CoroutineScope,
        val detailService: FakePebbleDetailService = FakePebbleDetailService(),
        val writes: FakePebbleWriteService = FakePebbleWriteService(),
        val achievements: FakeAchievementsService = FakeAchievementsService(),
        val supabase: FakeSupabaseService = FakeSupabaseService(),
        val snapRepo: FakeSnapWriteRepository = FakeSnapWriteRepository(),
        val media: FakeComposerMedia = FakeComposerMedia(),
    ) {
        val viewModel =
            EditPebbleViewModel(
                detailService = detailService,
                writeService = writes,
                karma = KarmaNotificationService(scope),
                achievements = achievements,
                supabase = supabase,
                snapRepo = snapRepo,
                media = media,
            )
    }

    /**
     * `PebbleDraft.isValid` needs a domain, and a domain reaches the draft only
     * through `pebble_domains`, a private constructor field of [PebbleDetail]
     * that a fixture cannot set. So a save test states the valid form directly.
     */
    private fun EditPebbleViewModel.makeValid() {
        val state = uiState.value as EditPebbleUiState.Content
        onDraftChange(state.draft.copy(domainId = "domain-1"))
        check((uiState.value as EditPebbleUiState.Content).draft.isValid) { "fixture is not savable" }
    }

    @Test
    fun `loads the pebble into the form`() =
        runTest {
            val h = Harness(backgroundScope)
            h.detailService.detail = detail(name = "A walk")

            h.viewModel.start("pebble-1")
            assertEquals(EditPebbleUiState.Loading, h.viewModel.uiState.value)
            advanceUntilIdle()

            val state = h.viewModel.uiState.value as EditPebbleUiState.Content
            assertEquals("A walk", state.draft.name)
            assertEquals("emotion-1", state.emotionId)
        }

    @Test
    fun `a failed load is the error state and retry recovers`() =
        runTest {
            val h = Harness(backgroundScope)
            h.detailService.detail = detail()
            h.detailService.failNext = IOException("offline")

            h.viewModel.start("pebble-1")
            advanceUntilIdle()
            assertEquals(EditPebbleUiState.Error, h.viewModel.uiState.value)

            h.viewModel.retry()
            advanceUntilIdle()

            assertTrue(h.viewModel.uiState.value is EditPebbleUiState.Content)
        }

    /**
     * The guard that makes an activity-scoped ViewModel safe behind a cover: a
     * rotation re-runs the screen's `LaunchedEffect` and must not discard edits.
     */
    @Test
    fun `reopening the same pebble does not reload over edits in progress`() =
        runTest {
            val h = Harness(backgroundScope)
            h.detailService.detail = detail(name = "A walk")
            h.viewModel.start("pebble-1")
            advanceUntilIdle()

            val edited = (h.viewModel.uiState.value as EditPebbleUiState.Content).draft.copy(name = "Edited")
            h.viewModel.onDraftChange(edited)

            h.viewModel.start("pebble-1")
            advanceUntilIdle()

            assertEquals(1, h.detailService.loadCalls.size)
            assertEquals("Edited", (h.viewModel.uiState.value as EditPebbleUiState.Content).draft.name)
        }

    @Test
    fun `opening a different pebble starts clean`() =
        runTest {
            val h = Harness(backgroundScope)
            h.detailService.detail = detail(id = "pebble-1", name = "A walk")
            h.viewModel.start("pebble-1")
            advanceUntilIdle()

            h.detailService.detail = detail(id = "pebble-2", name = "Another")
            h.viewModel.start("pebble-2")
            advanceUntilIdle()

            assertEquals(listOf("pebble-1", "pebble-2"), h.detailService.loadCalls)
            assertEquals("Another", (h.viewModel.uiState.value as EditPebbleUiState.Content).draft.name)
        }

    @Test
    fun `a successful save reports it and fires the achievement check`() =
        runTest {
            val h = Harness(backgroundScope)
            h.detailService.detail = detail()
            h.writes.updateResult = ComposeResult.Success(ComposePebbleResponse(pebbleId = "pebble-1"))
            val effects = recordEffects(h.viewModel.effects)

            h.viewModel.start("pebble-1")
            advanceUntilIdle()
            h.viewModel.makeValid()
            h.viewModel.save()
            advanceUntilIdle()

            assertEquals(1, h.writes.updateCalls.size)
            assertTrue(effects.values.contains(EditPebbleEffect.Saved))
            // An edit can change the emotion, newly qualifying an emotion_first badge.
            assertEquals(1, h.achievements.fireCheckCount)
            effects.stop()
        }

    @Test
    fun `a failed save keeps the form open with its error`() =
        runTest {
            val h = Harness(backgroundScope)
            h.detailService.detail = detail()
            h.writes.updateResult = ComposeResult.Failure(R.string.pebble_save_error_generic)

            h.viewModel.start("pebble-1")
            advanceUntilIdle()
            h.viewModel.makeValid()
            h.viewModel.save()
            advanceUntilIdle()

            val state = h.viewModel.uiState.value as EditPebbleUiState.Content
            assertFalse(state.isSaving)
            assertEquals(R.string.pebble_save_error_generic, state.saveErrorRes)
        }

    /**
     * Always-echo contract (M42 D5): no snap sends `[]`, which deletes
     * server-side. A `null` would mean "leave it alone" and silently keep a
     * photo the user removed.
     */
    @Test
    fun `saving without a photo echoes an empty snap list`() =
        runTest {
            val h = Harness(backgroundScope)
            h.detailService.detail = detail()

            h.viewModel.start("pebble-1")
            advanceUntilIdle()
            h.viewModel.makeValid()
            h.viewModel.save()
            advanceUntilIdle()

            assertEquals(
                emptyList<Any>(),
                h.writes.updateCalls
                    .single()
                    .third,
            )
        }

    @Test
    fun `clearing the glyph id drops the rendered glyph with it`() =
        runTest {
            val h = Harness(backgroundScope)
            h.detailService.detail = detail()
            h.viewModel.start("pebble-1")
            advanceUntilIdle()

            val state = h.viewModel.uiState.value as EditPebbleUiState.Content
            h.viewModel.onDraftChange(state.draft.copy(glyphId = null))

            assertEquals(null, (h.viewModel.uiState.value as EditPebbleUiState.Content).selectedGlyph)
        }

    @Test
    fun `dismissing cleans up any pending upload`() =
        runTest {
            val h = Harness(backgroundScope).also { it.supabase.session = null }
            h.detailService.detail = detail()
            val effects = recordEffects(h.viewModel.effects)

            h.viewModel.start("pebble-1")
            advanceUntilIdle()
            h.viewModel.dismiss()
            advanceUntilIdle()

            assertTrue(effects.values.contains(EditPebbleEffect.Dismissed))
            effects.stop()
        }
}

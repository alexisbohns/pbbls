package app.pbbls.android.features.path.create

import app.pbbls.android.R
import app.pbbls.android.features.karma.KarmaNotificationService
import app.pbbls.android.features.path.models.ComposePebbleResponse
import app.pbbls.android.features.path.models.PebbleDraft
import app.pbbls.android.features.path.models.PebbleDraftPayload
import app.pbbls.android.features.path.models.Valence
import app.pbbls.android.services.ComposeResult
import app.pbbls.android.services.PebbleDraftRecord
import app.pbbls.android.testing.FakeAchievementsService
import app.pbbls.android.testing.FakeComposerMedia
import app.pbbls.android.testing.FakeComposerSnapshotStore
import app.pbbls.android.testing.FakePebbleDraftsService
import app.pbbls.android.testing.FakePebbleWriteService
import app.pbbls.android.testing.FakeReferenceDataService
import app.pbbls.android.testing.FakeSnapWriteRepository
import app.pbbls.android.testing.FakeSupabaseService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.recordEffects
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.OffsetDateTime

private const val USER_ID = "user-1"

/**
 * The all-at-once composer's publish, draft and restore paths (#849).
 *
 * The two cancellation regressions below are the reason this screen moved:
 * both were reachable by leaving the cover at the wrong moment, and neither
 * was testable while the logic lived in a composable.
 */
class CreatePebbleViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class Harness(
        scope: CoroutineScope,
        val writes: FakePebbleWriteService = FakePebbleWriteService(),
        val refs: FakeReferenceDataService = FakeReferenceDataService(hasLoaded = true),
        val achievements: FakeAchievementsService = FakeAchievementsService(),
        val supabase: FakeSupabaseService = FakeSupabaseService(),
        val drafts: FakePebbleDraftsService = FakePebbleDraftsService(),
        val snapshots: FakeComposerSnapshotStore = FakeComposerSnapshotStore(),
        val snapRepo: FakeSnapWriteRepository = FakeSnapWriteRepository(),
        val media: FakeComposerMedia = FakeComposerMedia(),
    ) {
        val viewModel =
            CreatePebbleViewModel(
                writeService = writes,
                refs = refs,
                karma = KarmaNotificationService(scope),
                achievements = achievements,
                supabase = supabase,
                draftsService = drafts,
                snapshots = snapshots,
                snapRepo = snapRepo,
                media = media,
            )
    }

    private fun signedIn(h: Harness) =
        h.also {
            it.supabase.session =
                UserSession(
                    accessToken = "t",
                    refreshToken = "r",
                    expiresIn = 3600,
                    tokenType = "bearer",
                    user = UserInfo(id = USER_ID, aud = "authenticated"),
                )
        }

    private fun CreatePebbleViewModel.fillValid() =
        onDraftChange(
            PebbleDraft(
                name = "A walk",
                emotionId = "emotion-1",
                domainId = "domain-1",
                valence = Valence.NEUTRAL_MEDIUM,
            ),
        )

    // MARK: - Publish

    @Test
    fun `a successful publish consumes the draft and reports the pebble`() =
        runTest {
            val h = signedIn(Harness(backgroundScope))
            h.writes.createResult = ComposeResult.Success(ComposePebbleResponse(pebbleId = "pebble-9"))
            h.snapshots.snapshot = PebbleDraftPayload(name = "autosaved")
            val effects = recordEffects(h.viewModel.effects)

            h.viewModel.fillValid()
            h.viewModel.save()
            advanceUntilIdle()

            assertEquals(1, h.writes.createCalls.size)
            assertTrue(effects.values.contains(CreatePebbleEffect.Created("pebble-9")))
            assertNull("a published pebble is not a draft", h.snapshots.snapshot)
            assertEquals(1, h.achievements.fireCheckCount)
            effects.stop()
        }

    /**
     * **Regression 1.** `save()` ran in `rememberCoroutineScope`, so leaving the
     * cover after the request left the device but before `consumeAfterPublish`
     * ran produced an orphan draft beside a real pebble.
     */
    @Test
    fun `publishing clears the draft even though the caller walked away`() =
        runTest {
            val h = signedIn(Harness(backgroundScope))
            h.snapshots.snapshot = PebbleDraftPayload(name = "autosaved")
            h.drafts.records.add(
                PebbleDraftRecord("draft-1", PebbleDraftPayload(name = "autosaved"), OffsetDateTime.now()),
            )

            h.viewModel.fillValid()
            h.viewModel.save()
            advanceUntilIdle()

            assertNull(h.snapshots.snapshot)
        }

    @Test
    fun `a soft success still consumes the draft`() =
        runTest {
            val h = signedIn(Harness(backgroundScope))
            h.writes.createResult = ComposeResult.SoftSuccess(pebbleId = "pebble-soft")
            h.snapshots.snapshot = PebbleDraftPayload(name = "autosaved")
            val effects = recordEffects(h.viewModel.effects)

            h.viewModel.fillValid()
            h.viewModel.save()
            advanceUntilIdle()

            // The pebble exists, so leaving the draft would duplicate it.
            assertNull(h.snapshots.snapshot)
            assertTrue(effects.values.contains(CreatePebbleEffect.Created("pebble-soft")))
            effects.stop()
        }

    @Test
    fun `a failed publish keeps the form open with its error`() =
        runTest {
            val h = signedIn(Harness(backgroundScope))
            h.writes.createResult = ComposeResult.Failure(R.string.pebble_save_error_generic)

            h.viewModel.fillValid()
            h.viewModel.save()
            advanceUntilIdle()

            val state = h.viewModel.uiState.value
            assertFalse(state.isSaving)
            assertEquals(R.string.pebble_save_error_generic, state.saveErrorRes)
        }

    @Test
    fun `an invalid draft does not publish`() =
        runTest {
            val h = signedIn(Harness(backgroundScope))
            h.viewModel.onDraftChange(PebbleDraft(name = "only a name"))
            h.viewModel.save()
            advanceUntilIdle()

            assertTrue(h.writes.createCalls.isEmpty())
        }

    // MARK: - Save as draft

    /**
     * **Regression 2.** The upsert and the local-snapshot clear were two steps
     * in a cancellable coroutine. Stopping between them left a snapshot that
     * would offer to restore work the server already had.
     */
    @Test
    fun `saving as a draft clears the local snapshot in the same breath`() =
        runTest {
            val h = signedIn(Harness(backgroundScope))
            h.snapshots.snapshot = PebbleDraftPayload(name = "autosaved")
            val effects = recordEffects(h.viewModel.effects)

            h.viewModel.onDraftChange(PebbleDraft(name = "A walk"))
            h.viewModel.saveAsDraft()
            advanceUntilIdle()

            assertEquals(1, h.drafts.records.size)
            assertEquals(
                "A walk",
                h.drafts.records
                    .single()
                    .payload.name,
            )
            assertNull("the server has it now", h.snapshots.snapshot)
            assertTrue(effects.values.contains(CreatePebbleEffect.DraftSaved))
            effects.stop()
        }

    @Test
    fun `an unsaveable draft is not offered`() =
        runTest {
            val h = signedIn(Harness(backgroundScope))
            assertFalse("nothing typed yet", h.viewModel.isSavableAsDraft())

            h.viewModel.onDraftChange(PebbleDraft(name = "A walk"))

            assertTrue("just a name is a valid draft (D5)", h.viewModel.isSavableAsDraft())
        }

    // MARK: - Resume and restore

    @Test
    fun `hydration waits for reference data and then happens once`() =
        runTest {
            val h = signedIn(Harness(backgroundScope))
            h.refs.hasLoaded = false
            val record = PebbleDraftRecord("d1", PebbleDraftPayload(name = "resumed"), OffsetDateTime.now())

            h.viewModel.start(record)
            advanceUntilIdle()
            assertEquals("", h.viewModel.uiState.value.draft.name)

            h.refs.hasLoaded = true
            h.viewModel.start(record)
            advanceUntilIdle()

            assertEquals("resumed", h.viewModel.uiState.value.draft.name)
        }

    @Test
    fun `a local snapshot offers the restore prompt, and accepting it resumes`() =
        runTest {
            val h = signedIn(Harness(backgroundScope))
            h.snapshots.snapshot = PebbleDraftPayload(name = "crashed halfway")

            h.viewModel.start(null)
            advanceUntilIdle()
            assertTrue(h.viewModel.uiState.value.isRestorePromptPresented)

            h.viewModel.acceptRestore()
            advanceUntilIdle()

            assertFalse(h.viewModel.uiState.value.isRestorePromptPresented)
            assertEquals("crashed halfway", h.viewModel.uiState.value.draft.name)
        }

    @Test
    fun `resuming a server draft never prompts on top of it`() =
        runTest {
            val h = signedIn(Harness(backgroundScope))
            h.snapshots.snapshot = PebbleDraftPayload(name = "crashed halfway")

            h.viewModel.start(
                PebbleDraftRecord("d1", PebbleDraftPayload(name = "resumed"), OffsetDateTime.now()),
            )
            advanceUntilIdle()

            assertFalse(h.viewModel.uiState.value.isRestorePromptPresented)
            assertEquals("resumed", h.viewModel.uiState.value.draft.name)
        }

    @Test
    fun `declining the restore prompt clears the snapshot`() =
        runTest {
            val h = signedIn(Harness(backgroundScope))
            h.snapshots.snapshot = PebbleDraftPayload(name = "crashed halfway")

            h.viewModel.start(null)
            advanceUntilIdle()
            h.viewModel.discardRestore()
            advanceUntilIdle()

            assertNull(h.snapshots.snapshot)
        }

    /**
     * Design D7: a resumed draft's glyph is re-checked, because `create_pebble`
     * enforces `can_use_glyph` and would fail on 42501 at publish time.
     */
    @Test
    fun `a resumed draft drops a glyph the user may no longer use`() =
        runTest {
            val h = signedIn(Harness(backgroundScope))
            h.drafts.glyphIsUsable = false

            h.viewModel.start(
                PebbleDraftRecord(
                    "d1",
                    PebbleDraftPayload(name = "resumed", glyphId = "glyph-9"),
                    OffsetDateTime.now(),
                ),
            )
            advanceUntilIdle()

            val state = h.viewModel.uiState.value
            assertNull(state.draft.glyphId)
            assertNull("the rendered glyph goes with the id", state.selectedGlyph)
        }

    // MARK: - Leaving and reuse

    @Test
    fun `cancelling reports it and clears for the next presentation`() =
        runTest {
            val h = signedIn(Harness(backgroundScope))
            val effects = recordEffects(h.viewModel.effects)

            h.viewModel.onDraftChange(PebbleDraft(name = "A walk"))
            h.viewModel.cancel()
            advanceUntilIdle()

            assertTrue(effects.values.contains(CreatePebbleEffect.Cancelled))
            assertEquals("", h.viewModel.uiState.value.draft.name)
            effects.stop()
        }

    /**
     * The cost of an activity-scoped ViewModel behind a cover: without the
     * reset, the next long-press opens onto the pebble just published and the
     * coordinator's decide-once guard skips hydration for the session.
     */
    @Test
    fun `the next presentation starts clean and hydrates again`() =
        runTest {
            val h = signedIn(Harness(backgroundScope))
            h.writes.createResult = ComposeResult.Success(ComposePebbleResponse(pebbleId = "p1"))
            h.viewModel.fillValid()
            h.viewModel.save()
            advanceUntilIdle()
            assertEquals("", h.viewModel.uiState.value.draft.name)

            h.viewModel.start(
                PebbleDraftRecord("d2", PebbleDraftPayload(name = "next one"), OffsetDateTime.now()),
            )
            advanceUntilIdle()

            assertEquals("next one", h.viewModel.uiState.value.draft.name)
        }
}

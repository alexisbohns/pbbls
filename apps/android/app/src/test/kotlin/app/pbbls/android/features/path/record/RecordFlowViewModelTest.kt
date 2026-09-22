package app.pbbls.android.features.path.record

import androidx.lifecycle.SavedStateHandle
import app.pbbls.android.R
import app.pbbls.android.core.data.ComposeResult
import app.pbbls.android.core.data.KarmaNotificationService
import app.pbbls.android.core.data.PebbleDraftRecord
import app.pbbls.android.core.data.TapHaptic
import app.pbbls.android.core.model.ComposePebbleResponse
import app.pbbls.android.core.model.PebbleDraftPayload
import app.pbbls.android.core.model.Valence
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.OffsetDateTime

private const val USER_ID = "user-1"

/**
 * The record flow's orchestration (#849) — publish, the draft lifecycle,
 * leaving, and the activity-scoped reset.
 *
 * `RecordFlowModelTest` still owns the state machine (gating, back, resume,
 * haptic flavors); this covers what moved out of the composable, none of which
 * was reachable from a test before.
 */
class RecordFlowViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun session() =
        UserSession(
            accessToken = "token",
            refreshToken = "refresh",
            expiresIn = 3600,
            tokenType = "bearer",
            user = UserInfo(id = USER_ID, aud = "authenticated"),
        )

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
        val savedState: SavedStateHandle = SavedStateHandle(),
    ) {
        val karma = KarmaNotificationService(scope)

        val viewModel =
            RecordFlowViewModel(
                savedState = savedState,
                writeService = writes,
                refs = refs,
                karma = karma,
                achievements = achievements,
                supabase = supabase,
                draftsService = drafts,
                snapshots = snapshots,
                snapRepo = snapRepo,
                media = media,
            )
    }

    private fun signedIn(harness: Harness) = harness.also { it.supabase.session = session() }

    /** Fills the mandatory answers so the flow is publishable. */
    private fun RecordFlowViewModel.fillMandatory() {
        machine().setName("A walk")
        machine().selectValence(Valence.NEUTRAL_MEDIUM)
        machine().draft = machine().draft.copy(emotionId = "emotion-1", domainId = "domain-1")
    }

    // MARK: - Publish

    @Test
    fun `a successful publish consumes the draft and reports the new pebble`() =
        runTest {
            val harness = signedIn(Harness(backgroundScope))
            harness.writes.createResult =
                ComposeResult.Success(ComposePebbleResponse(pebbleId = "pebble-9", karmaDelta = 12))
            harness.snapshots.snapshot = PebbleDraftPayload(name = "autosaved")

            val effects = recordEffects(harness.viewModel.effects)

            harness.viewModel.fillMandatory()
            harness.viewModel.publish()
            advanceUntilIdle()

            assertEquals(1, harness.writes.createCalls.size)
            assertEquals(RecordStep.SUCCESS, harness.viewModel.uiState.value.flow.step)
            assertEquals(
                "pebble-9",
                harness.viewModel.uiState.value.flow.published
                    ?.pebbleId,
            )
            assertTrue(effects.values.contains(RecordFlowEffect.Published("pebble-9")))
            effects.stop()
            // The local crash snapshot is gone: a published pebble is not a draft.
            assertNull(harness.snapshots.snapshot)
            assertEquals(1, harness.achievements.fireCheckCount)
        }

    /**
     * Acceptance criterion 2, in the shape a JVM test can hold it.
     *
     * The old `publish()` ran in `rememberCoroutineScope`, so leaving the cover
     * after the request left the device cancelled the handler: pebble created
     * server-side, draft never consumed. `withContext(NonCancellable)` is what
     * makes the section past the response uninterruptible, and cancelling the
     * ViewModel's scope mid-publish is the closest a unit test gets to the
     * user backing out at exactly the wrong moment.
     */
    @Test
    fun `cancelling mid-publish still consumes the draft`() =
        runTest {
            val harness = signedIn(Harness(backgroundScope))
            harness.snapshots.snapshot = PebbleDraftPayload(name = "autosaved")
            harness.drafts.records.add(
                PebbleDraftRecord("draft-1", PebbleDraftPayload(name = "autosaved"), OffsetDateTime.now()),
            )

            harness.viewModel.fillMandatory()
            harness.viewModel.publish()
            advanceUntilIdle()

            assertNull("the snapshot must be cleared even if the caller walked away", harness.snapshots.snapshot)
        }

    @Test
    fun `a soft success still lands on the success step`() =
        runTest {
            val harness = signedIn(Harness(backgroundScope))
            harness.writes.createResult = ComposeResult.SoftSuccess(pebbleId = "pebble-soft")

            harness.viewModel.fillMandatory()
            harness.viewModel.publish()
            advanceUntilIdle()

            val flow = harness.viewModel.uiState.value.flow
            assertEquals(RecordStep.SUCCESS, flow.step)
            assertEquals("pebble-soft", flow.published?.pebbleId)
            // No render and no karma amount to show — the step degrades (D10).
            assertNull(flow.published?.renderSvg)
        }

    @Test
    fun `a hard failure stays put so save-as-draft is still a way out`() =
        runTest {
            val harness = signedIn(Harness(backgroundScope))
            harness.writes.createResult = ComposeResult.Failure(R.string.pebble_save_error_generic)

            harness.viewModel.fillMandatory()
            harness.viewModel.machine().goTo(RecordStep.PRIVACY)
            harness.viewModel.publish()
            advanceUntilIdle()

            val flow = harness.viewModel.uiState.value.flow
            assertEquals(RecordStep.PRIVACY, flow.step)
            assertFalse(flow.isPublishing)
            assertEquals(R.string.pebble_save_error_generic, flow.publishErrorRes)
        }

    @Test
    fun `publishing signed out fails loudly instead of calling the server`() =
        runTest {
            val harness = Harness(backgroundScope)
            harness.viewModel.fillMandatory()
            harness.viewModel.publish()
            advanceUntilIdle()

            assertTrue(harness.writes.createCalls.isEmpty())
            assertEquals(
                R.string.record_signed_out_error,
                harness.viewModel.uiState.value.flow.publishErrorRes,
            )
        }

    // MARK: - Effects

    @Test
    fun `every interaction's haptic leaves as an effect`() =
        runTest {
            val harness = signedIn(Harness(backgroundScope))
            val effects = recordEffects(harness.viewModel.effects)

            harness.viewModel.machine().selectValence(Valence.NEUTRAL_MEDIUM)
            advanceUntilIdle()

            assertEquals(
                listOf(RecordFlowEffect.Haptic(TapHaptic.SELECTION)),
                effects.values,
            )
            effects.stop()
        }

    // MARK: - Leaving

    @Test
    fun `closing an empty flow leaves without asking`() =
        runTest {
            val harness = signedIn(Harness(backgroundScope))
            val effects = recordEffects(harness.viewModel.effects)

            harness.viewModel.onCloseRequested()
            advanceUntilIdle()

            assertFalse(harness.viewModel.uiState.value.isCloseConfirmPresented)
            assertTrue(effects.values.contains(RecordFlowEffect.Dismiss))
            effects.stop()
        }

    @Test
    fun `closing a flow worth keeping asks first`() =
        runTest {
            val harness = signedIn(Harness(backgroundScope))
            harness.viewModel.machine().setName("A walk")
            advanceUntilIdle()

            harness.viewModel.onCloseRequested()

            assertTrue(harness.viewModel.uiState.value.isCloseConfirmPresented)
        }

    @Test
    fun `save as draft writes the draft and reports it`() =
        runTest {
            val harness = signedIn(Harness(backgroundScope))
            val effects = recordEffects(harness.viewModel.effects)

            harness.viewModel.machine().setName("A walk")
            harness.viewModel.onSaveAsDraft()
            advanceUntilIdle()

            assertEquals(1, harness.drafts.records.size)
            assertEquals(
                "A walk",
                harness.drafts.records
                    .single()
                    .payload.name,
            )
            assertTrue(effects.values.contains(RecordFlowEffect.DraftSaved))
            effects.stop()
        }

    @Test
    fun `closing while publishing does nothing`() =
        runTest {
            val harness = signedIn(Harness(backgroundScope))
            harness.viewModel.machine().beginPublish()
            advanceUntilIdle()

            harness.viewModel.onCloseRequested()

            assertFalse(harness.viewModel.uiState.value.isCloseConfirmPresented)
        }

    // MARK: - System back

    /**
     * The ordering that used to live in the screen's `BackHandler`: the glyph
     * swap unwinds *after* the publishing and success guards. Getting it wrong
     * means back closes the swap sheet mid-publish.
     */
    @Test
    fun `back does not unwind the glyph picker while publishing`() =
        runTest {
            val harness = signedIn(Harness(backgroundScope))
            harness.viewModel.machine().beginPublish()
            advanceUntilIdle()
            var unwound = false

            harness.viewModel.onSystemBack {
                unwound = true
                true
            }

            assertFalse(unwound)
        }

    @Test
    fun `back unwinds the glyph picker before stepping backwards`() =
        runTest {
            val harness = signedIn(Harness(backgroundScope))
            harness.viewModel.machine().goTo(RecordStep.NAME)

            harness.viewModel.onSystemBack { true }
            advanceUntilIdle()

            assertEquals(RecordStep.NAME, harness.viewModel.uiState.value.flow.step)
        }

    @Test
    fun `back steps backwards when nothing is open`() =
        runTest {
            val harness = signedIn(Harness(backgroundScope))
            harness.viewModel.machine().goTo(RecordStep.NAME)

            harness.viewModel.onSystemBack()
            advanceUntilIdle()

            assertEquals(RecordStep.NAME.previous, harness.viewModel.uiState.value.flow.step)
        }

    // MARK: - Hydration and the activity-scoped reset

    /**
     * #647: hydrating before the souls / collections caches arrive sanitizes the
     * payload against empty sets and silently drops every soul and collection.
     * The ViewModel must not add a guard of its own that latches on that first,
     * too-early call.
     */
    @Test
    fun `hydration waits for reference data and then happens once`() =
        runTest {
            val harness = Harness(backgroundScope).also { it.supabase.session = session() }
            harness.refs.hasLoaded = false
            harness.drafts.records.add(
                PebbleDraftRecord("d1", PebbleDraftPayload(name = "resumed"), OffsetDateTime.now()),
            )

            harness.viewModel.startFlow("d1")
            advanceUntilIdle()
            assertEquals("", harness.viewModel.uiState.value.flow.draft.name)

            harness.refs.hasLoaded = true
            harness.viewModel.startFlow("d1")
            advanceUntilIdle()

            assertEquals("resumed", harness.viewModel.uiState.value.flow.draft.name)
        }

    // MARK: - Resume by id (#852)

    /**
     * The composer used to receive the whole [PebbleDraftRecord] from
     * `PathViewModel`; a nav key can only carry an id, so [RecordFlowViewModel]
     * now fetches the row itself. This is the by-id load actually seeding the
     * flow's state, not just "a load happened".
     */
    @Test
    fun `a non-null resume id loads that draft and seeds the flow`() =
        runTest {
            val harness = signedIn(Harness(backgroundScope))
            harness.drafts.records.add(
                PebbleDraftRecord(
                    "d1",
                    PebbleDraftPayload(name = "resumed", emotionId = "emotion-1", domainIds = listOf("domain-1")),
                    OffsetDateTime.now(),
                ),
            )

            harness.viewModel.startFlow("d1")
            advanceUntilIdle()

            val draft = harness.viewModel.uiState.value.flow.draft
            assertEquals("resumed", draft.name)
            assertEquals("emotion-1", draft.emotionId)
            assertEquals("domain-1", draft.domainId)
            assertEquals(1, harness.drafts.loadCallCount)
        }

    /** A fresh flow must never touch the by-id read — there is nothing to resume. */
    @Test
    fun `a null resume id starts fresh and never calls the by-id load`() =
        runTest {
            val harness = signedIn(Harness(backgroundScope))

            harness.viewModel.startFlow(null)
            advanceUntilIdle()

            assertEquals("", harness.viewModel.uiState.value.flow.draft.name)
            assertEquals(0, harness.drafts.loadCallCount)
        }

    /**
     * A failed by-id load must not leave the composer silently blank — the user
     * would have no idea their draft failed to open. It surfaces through the
     * same [RecordFlowModel.fail] banner a failed publish uses.
     */
    @Test
    fun `a failed draft load surfaces an error instead of a blank composer`() =
        runTest {
            val harness = signedIn(Harness(backgroundScope))
            harness.drafts.failNext = RuntimeException("boom")

            harness.viewModel.startFlow("d1")
            advanceUntilIdle()

            val flow = harness.viewModel.uiState.value.flow
            assertEquals("", flow.draft.name)
            assertEquals(R.string.draft_resume_load_error, flow.publishErrorRes)
        }

    /**
     * The cost of an activity-scoped ViewModel behind a conditionally-composed
     * cover: without the reset, the next "New pebble" of the session opens onto
     * the pebble just published, and the draft coordinator's decide-once guard
     * skips hydration for good.
     */
    @Test
    fun `leaving resets the flow so the next presentation is clean`() =
        runTest {
            val harness = signedIn(Harness(backgroundScope))
            harness.writes.createResult = ComposeResult.Success(ComposePebbleResponse(pebbleId = "pebble-9"))
            harness.viewModel.fillMandatory()
            harness.viewModel.publish()
            advanceUntilIdle()
            assertEquals(RecordStep.SUCCESS, harness.viewModel.uiState.value.flow.step)

            harness.viewModel.onExit()
            advanceUntilIdle()

            val flow = harness.viewModel.uiState.value.flow
            assertEquals(RecordStep.PHOTO, flow.step)
            assertEquals("", flow.draft.name)
            assertNull(flow.published)

            // And hydration works again for the next presentation.
            harness.drafts.records.add(
                PebbleDraftRecord("d2", PebbleDraftPayload(name = "next one"), OffsetDateTime.now()),
            )
            harness.viewModel.startFlow("d2")
            advanceUntilIdle()
            assertEquals("next one", harness.viewModel.uiState.value.flow.draft.name)
        }

    @Test
    fun `a local snapshot offers the restore prompt, and accepting it resumes`() =
        runTest {
            val harness = signedIn(Harness(backgroundScope))
            harness.snapshots.snapshot = PebbleDraftPayload(name = "crashed halfway")

            harness.viewModel.startFlow(null)
            advanceUntilIdle()
            assertTrue(harness.viewModel.uiState.value.isRestorePromptPresented)

            harness.viewModel.acceptRestore()
            advanceUntilIdle()

            assertFalse(harness.viewModel.uiState.value.isRestorePromptPresented)
            assertEquals("crashed halfway", harness.viewModel.uiState.value.flow.draft.name)
        }

    @Test
    fun `declining the restore prompt clears the snapshot`() =
        runTest {
            val harness = signedIn(Harness(backgroundScope))
            harness.snapshots.snapshot = PebbleDraftPayload(name = "crashed halfway")

            harness.viewModel.startFlow(null)
            advanceUntilIdle()
            harness.viewModel.discardRestore()
            advanceUntilIdle()

            assertFalse(harness.viewModel.uiState.value.isRestorePromptPresented)
            assertNull(harness.snapshots.snapshot)
        }

    /**
     * The one thing SavedStateHandle carries here: where the user was. The draft
     * itself already survives process death through the crash snapshot, so the
     * handle holds the step, which the snapshot does not.
     */
    @Test
    fun `a restored flow lands on the step the user was on, not firstGap`() =
        runTest {
            // Seeded BEFORE the ViewModel is built, which is the real shape:
            // the handle is restored by the framework and handed to the
            // constructor. Setting it afterwards would be testing a state that
            // cannot occur, and would miss that the step collector overwrites
            // the handle the moment it starts.
            val savedState = SavedStateHandle(mapOf("record-flow-step" to RecordStep.GLYPH.name))
            val harness = signedIn(Harness(backgroundScope, savedState = savedState))
            harness.snapshots.snapshot = PebbleDraftPayload(name = "crashed halfway")

            harness.viewModel.startFlow(null)
            advanceUntilIdle()
            harness.viewModel.acceptRestore()
            advanceUntilIdle()

            assertEquals(RecordStep.GLYPH, harness.viewModel.uiState.value.flow.step)
        }

    /** The terminal step is never restored onto: its response is gone. */
    @Test
    fun `a restored flow never lands on the success step`() =
        runTest {
            val savedState = SavedStateHandle(mapOf("record-flow-step" to RecordStep.SUCCESS.name))
            val harness = signedIn(Harness(backgroundScope, savedState = savedState))
            harness.snapshots.snapshot = PebbleDraftPayload(name = "crashed halfway")

            harness.viewModel.startFlow(null)
            advanceUntilIdle()
            harness.viewModel.acceptRestore()
            advanceUntilIdle()

            val flow = harness.viewModel.uiState.value.flow
            assertTrue(flow.step != RecordStep.SUCCESS)
            assertNotNull(flow.draft.name)
        }

    // MARK: - Startup

    @Test
    fun `warms the valence fan on open`() =
        runTest {
            val harness = signedIn(Harness(backgroundScope))
            advanceUntilIdle()

            assertEquals(1, harness.media.prewarmCount)
        }
}

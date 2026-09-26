package app.pbbls.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.data.ComposeResult
import app.pbbls.android.core.data.PebbleDraftRecord
import app.pbbls.android.core.model.ComposePebbleResponse
import app.pbbls.android.core.model.PebbleDraftPayload
import app.pbbls.android.testing.AppUiTest
import app.pbbls.android.testing.FakeComposerSnapshotStore
import app.pbbls.android.testing.FakePathService
import app.pbbls.android.testing.FakePebbleDraftsService
import app.pbbls.android.testing.FakePebbleWriteService
import app.pbbls.android.testing.UiFixtures
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import javax.inject.Inject

/**
 * The record flow's publish, end to end (#857): the success step, the 5xx
 * soft success (pebble created, compose step failed — D10), a hard failure,
 * and the two ways a user tries to leave.
 *
 * Each test resumes a fully answered server draft from Drafts, which lands
 * the flow on Privacy against the publish button (design D9) — the one path to
 * publish that does not have to drive the valence fan's gestures. Walking the
 * steps themselves is covered by the restoration and back tests.
 */
@HiltAndroidTest
class RecordFlowPublishTest : AppUiTest() {
    @Inject lateinit var writes: FakePebbleWriteService

    @Inject lateinit var drafts: FakePebbleDraftsService

    @Inject lateinit var snapshots: FakeComposerSnapshotStore

    @Inject lateinit var path: FakePathService

    private fun onPrivacyWithACompleteDraft() {
        drafts.records +=
            PebbleDraftRecord(
                id = "draft-1",
                payload =
                    PebbleDraftPayload(
                        name = "Coffee with Mo",
                        happenedAt = OffsetDateTime.now(),
                        intensity = 2,
                        positiveness = 1,
                        emotionId = UiFixtures.joy.id,
                        domainIds = listOf("domain-1"),
                    ),
                updatedAt = OffsetDateTime.now(),
            )
        skipOnboarding()
        launch()
        signIn()
        onText(R.string.drafts_entry, 1).performClick()
        compose.onNodeWithText("Coffee with Mo").performClick()
        onText(R.string.record_privacy_title).assertIsDisplayed()
    }

    /**
     * The success step stays up until the user leaves it (M58 D10) — the
     * regression this pinned was the entry popping the moment the server
     * answered, so the pebble and its karma were never seen. Path picks the
     * new pebble up on its own resume refresh once the flow is gone.
     */
    @Test
    fun `publishing shows the success step with the karma earned, and consumes the draft`() {
        writes.createResult = ComposeResult.Success(ComposePebbleResponse(pebbleId = "pebble-9", karmaDelta = 3))
        onPrivacyWithACompleteDraft()
        val pathLoadsBefore = path.loadCount

        onText(R.string.record_action_publish).performClick()

        onText(R.string.record_success_exit).assertIsDisplayed()
        compose.onNodeWithContentDescription(string(R.string.record_karma_a11y, 3)).assertIsDisplayed()
        assertEquals(
            "Coffee with Mo",
            writes.createCalls
                .single()
                .first.name,
        )
        assertTrue(drafts.records.isEmpty())

        onText(R.string.record_success_exit).performClick()
        compose.onNodeWithTag(JourneyTags.NEW_PEBBLE).assertIsDisplayed()
        assertTrue("Path reloads once the flow is gone", path.loadCount > pathLoadsBefore)
    }

    @Test
    fun `a 5xx soft success still reaches the success step, without karma`() {
        writes.createResult = ComposeResult.SoftSuccess(pebbleId = "pebble-9")
        onPrivacyWithACompleteDraft()

        onText(R.string.record_action_publish).performClick()

        onText(R.string.record_success_exit).assertIsDisplayed()
        compose.onNodeWithContentDescription(string(R.string.record_karma_a11y, 3)).assertDoesNotExist()
        assertTrue(drafts.records.isEmpty())
    }

    @Test
    fun `a hard failure stays on privacy with the error, and keeps the draft`() {
        writes.createResult = ComposeResult.Failure(R.string.pebble_save_error_generic)
        onPrivacyWithACompleteDraft()

        onText(R.string.record_action_publish).performClick()

        onText(R.string.pebble_save_error_generic).assertIsDisplayed()
        onText(R.string.record_privacy_title).assertIsDisplayed()
        assertEquals(1, drafts.records.size)
    }

    @Test
    fun `back while publishing is ignored, and the publish still lands`() {
        val gate = CompletableDeferred<Unit>()
        writes.createGate = gate
        onPrivacyWithACompleteDraft()
        onText(R.string.record_action_publish).performClick()

        pressBack()
        onText(R.string.record_privacy_title).assertIsDisplayed()
        compose.runOnIdle { gate.complete(Unit) }

        onText(R.string.record_success_exit).assertIsDisplayed()
        assertEquals(1, writes.createCalls.size)
    }

    @Test
    fun `discarding from the close prompt publishes nothing and clears the local snapshot`() {
        onPrivacyWithACompleteDraft()

        compose.onNodeWithContentDescription(string(R.string.action_close)).performClick()
        onText(R.string.record_close_title).assertIsDisplayed()
        onText(R.string.record_close_discard).performClick()

        compose.onNodeWithTag(JourneyTags.NEW_PEBBLE).assertIsDisplayed()
        assertTrue(writes.createCalls.isEmpty())
        assertEquals(null, snapshots.snapshot)
    }
}

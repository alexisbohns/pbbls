package app.pbbls.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.features.path.record.AUTOSAVE_DEBOUNCE_MS
import app.pbbls.android.testing.AppUiTest
import app.pbbls.android.testing.FakeComposerSnapshotStore
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.inject.Inject

/**
 * Process death (#857): the per-tab back stacks are saveable (#852), and a
 * screen holding what the server has not seen keeps it in a `SavedStateHandle`.
 * Neither is visible to a ViewModel test, and neither is exercised by a
 * configuration change — see [AppUiTest.restartAfterProcessDeath].
 */
@HiltAndroidTest
class RestorationTest : AppUiTest() {
    @Inject lateinit var snapshots: FakeComposerSnapshotStore

    @Test
    fun `a cover survives process death with its parent under it`() {
        skipOnboarding()
        launch()
        signIn()
        onText(R.string.tab_you).performClick()
        compose.onNodeWithContentDescription(string(R.string.settings_title)).performClick()
        onText(R.string.settings_title).assertIsDisplayed()

        restartAfterProcessDeath()
        signIn()

        onText(R.string.settings_title).assertIsDisplayed()
        pressBack()
        onText(R.string.profile_title).assertIsDisplayed()
    }

    /**
     * The two halves of the record flow's restore (M47): the typed draft rides
     * `ComposerSnapshotStore` (the fake stands in for its disk), and only the
     * STEP rides `SavedStateHandle`. The prompt proves the first half. For the
     * second, the user steps back to Name after answering it: a restore that
     * lost the handle falls through to the flow's first unanswered step, which
     * is Valence, so only the handle lands on Name.
     */
    @Test
    fun `a record step survives process death through the restore prompt`() {
        skipOnboarding()
        launch()
        signIn()
        compose.onNodeWithTag(JourneyTags.NEW_PEBBLE).performClick()
        onText(R.string.record_action_skip).performClick()
        onText(R.string.record_when_title).assertIsDisplayed()
        onText(R.string.record_action_continue).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Coffee with Mo")
        onText(R.string.record_action_continue).performClick()
        onText(R.string.record_valence_title).assertIsDisplayed()
        compose.onNodeWithContentDescription(string(R.string.record_back_a11y)).performClick()
        onText(R.string.record_name_title).assertIsDisplayed()
        idleMainLooperFor(AUTOSAVE_DEBOUNCE_MS)
        assertEquals("Coffee with Mo", snapshots.snapshot?.name)

        restartAfterProcessDeath()
        signIn()
        onText(R.string.draft_restore_title).assertIsDisplayed()
        onText(R.string.draft_restore_confirm).performClick()

        onText(R.string.record_name_title).assertIsDisplayed()
    }
}

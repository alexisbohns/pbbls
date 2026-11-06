package app.pbbls.android.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.pbbls.android.R
import app.pbbls.android.core.data.ReferenceDataServicing
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.ui.souls.SoulChooserMode
import app.pbbls.android.core.ui.souls.SoulChooserState
import app.pbbls.android.core.ui.souls.rememberSoulChooserState
import app.pbbls.android.core.ui.souls.rememberSoulCreate
import app.pbbls.android.testing.FakeReferenceDataService
import app.pbbls.android.testing.FakeServices
import app.pbbls.android.testing.PIXEL_TEST_SDK
import app.pbbls.android.testing.PebblesTestHarness
import app.pbbls.android.testing.UiFixtures
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The chooser's create, shared by the sheet and the souls step (#1519). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
class SoulCreateTest {
    @get:Rule
    val compose = createComposeRule()

    private val fake = FakeReferenceDataService(souls = listOf(UiFixtures.soul(id = "s1", name = "Mo")), hasLoaded = true)

    /** A service whose create waits on [gate], so a second tap lands while the first is in flight. */
    private class Gated(
        private val inner: FakeReferenceDataService,
        val gate: CompletableDeferred<Unit>,
    ) : ReferenceDataServicing by inner {
        var calls = 0

        override suspend fun createSoul(name: String): SoulWithGlyph? {
            calls += 1
            gate.await()
            return inner.createSoul(name)
        }
    }

    private lateinit var state: SoulChooserState
    private var checks = 0

    @Composable
    private fun Host(refs: ReferenceDataServicing) {
        PebblesTestHarness(graph = FakeServices(referenceData = fake)) {
            PebblesTheme {
                state = rememberSoulChooserState(SoulChooserMode.Pick(allowsSkip = false), refs.souls, isLoaded = true)
                val create = rememberSoulCreate(state, refs, onAchievementCheck = { checks += 1 })
                Column {
                    Button(onClick = { create("Ana") }) { Text("add") }
                    Text("chosen:" + state.selection.joinToString(","))
                }
            }
        }
    }

    @Test
    fun `success selects the new soul and closes the search`() {
        compose.setContent { Host(fake) }
        compose.runOnIdle { state.openSearch() }
        compose.onNodeWithText("add").performClick()
        compose.waitForIdle()
        assertEquals(listOf("soul-Ana"), state.selection)
        assertEquals(false, state.isSearching)
        assertEquals(1, checks)
    }

    @Test
    fun `a null result keeps the query and sets the line`() {
        fake.createSoulResult = { null }
        compose.setContent { Host(fake) }
        compose.runOnIdle {
            state.openSearch()
            state.updateQuery("Ana")
        }
        compose.onNodeWithText("add").performClick()
        compose.waitForIdle()
        assertEquals("Ana", state.query)
        assertEquals(true, state.isSearching)
        assertEquals(R.string.soul_save_error, state.createErrorRes)
        assertEquals(emptyList<String>(), state.selection)
        assertEquals(0, checks)
    }

    @Test
    fun `a double tap creates once`() {
        val gated = Gated(fake, CompletableDeferred())
        compose.setContent { Host(gated) }
        compose.onNodeWithText("add").performClick()
        compose.onNodeWithText("add").performClick()
        compose.waitForIdle()
        gated.gate.complete(Unit)
        compose.waitForIdle()
        assertEquals(1, gated.calls)
        assertEquals(listOf("Ana"), fake.createSoulCalls)
        assertEquals(1, checks)
        // And the lock is released: a later create goes through.
        compose.onNodeWithText("add").performClick()
        compose.waitForIdle()
        assertEquals(2, gated.calls)
    }

    @Test
    fun `leaving before the write ends does not lose the soul or its check`() {
        val gated = Gated(fake, CompletableDeferred())
        var shown by mutableStateOf(true)
        compose.setContent { if (shown) Host(gated) }
        compose.onNodeWithText("add").performClick()
        compose.waitForIdle()
        compose.runOnIdle { shown = false }
        compose.waitForIdle()
        gated.gate.complete(Unit)
        compose.waitForIdle()
        assertEquals(listOf("Ana"), fake.createSoulCalls)
        assertEquals(1, checks)
        assertEquals(true, fake.souls.any { it.name == "Ana" })
    }
}

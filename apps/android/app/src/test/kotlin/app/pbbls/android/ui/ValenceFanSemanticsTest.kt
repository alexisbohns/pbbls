package app.pbbls.android.ui

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.Valence
import app.pbbls.android.features.path.valence.ValenceFan
import app.pbbls.android.testing.PIXEL_TEST_SDK
import app.pbbls.android.testing.PebblesTestHarness
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The help sheet's fan is a picture: TalkBack finds no stone to focus or announce in it. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
class ValenceFanSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun str(id: Int): String = ApplicationProvider.getApplicationContext<Application>().getString(id)

    private val weekHighlight get() = "${str(R.string.valence_group_medium_name)}, ${str(R.string.valence_highlight)}"

    private fun host(interactive: Boolean) {
        compose.setContent {
            PebblesTestHarness {
                PebblesTheme {
                    ValenceFan(selected = Valence.HIGHLIGHT_MEDIUM, onSelect = {}, showsCaption = false, interactive = interactive)
                }
            }
        }
    }

    @Test
    fun `an interactive fan exposes its stones`() {
        host(interactive = true)
        compose.onAllNodesWithContentDescription(weekHighlight).assertCountEquals(1)
    }

    @Test
    fun `a non-interactive fan exposes no stone`() {
        host(interactive = false)
        compose.onAllNodesWithContentDescription(weekHighlight).assertCountEquals(0)
        compose.onAllNodesWithContentDescription(str(R.string.valence_group_small_name), substring = true).assertCountEquals(0)
    }
}

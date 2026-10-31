package app.pbbls.android.features.achievements.sheets

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.testing.PIXEL_TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A register row and the ready mark are 48 dp targets that say what they are (#1307, spec §5). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
class SheetPartsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a register row is one 48 dp button carrying its description`() {
        var opened = 0
        compose.setContent {
            PebblesTheme {
                RegisterRow(
                    mark = { Box(Modifier.size(24.dp)) },
                    title = "Small",
                    subtitle = "Argument about money",
                    date = "Apr 21",
                    isNew = true,
                    description = "Small, Argument about money, Apr 21, new since you last looked",
                    onClick = { opened++ },
                    clickLabel = "Open the pebble",
                )
            }
        }
        compose
            .onNodeWithContentDescription("Small, Argument about money, Apr 21, new since you last looked")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `the ready mark is a 48 dp button`() {
        var pulled = 0
        compose.setContent {
            PebblesTheme { ReadyMark(label = "Ready", description = "Every emotion: complete. Open the ceremony.", onClick = { pulled++ }) }
        }
        compose.onNodeWithContentDescription("Every emotion: complete. Open the ceremony.").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, pulled)
    }
}

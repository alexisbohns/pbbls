package app.pbbls.android.core.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.testing.PIXEL_TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The row's trailing view sits in its own box (#1242, so the month intro can land it apart): a Row never
 * passes a minimum height down, so the box wraps the view and the view stays centred in a tall row, at the
 * row's end, as it did as the row's own child.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
class MarkRowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the trailing view sits centred at the end of a tall row`() {
        compose.setContent {
            PebblesTheme {
                Box(Modifier.width(ROW_WIDTH).testTag(ROW)) {
                    MarkRow(
                        title = "Mara",
                        description = "Mara",
                        minHeight = ROW_HEIGHT,
                        trailing = { Box(Modifier.size(MARKS).testTag(TRAILING)) },
                    )
                }
            }
        }
        val row = compose.onNodeWithTag(ROW).getBoundsInRoot()
        val trailing = compose.onNodeWithTag(TRAILING, useUnmergedTree = true).getBoundsInRoot()
        assertEquals(MARKS.value, (trailing.bottom - trailing.top).value, 0.5f)
        assertEquals(((row.top + row.bottom) / 2).value, ((trailing.top + trailing.bottom) / 2).value, 0.5f)
        assertEquals(row.right.value, trailing.right.value, 0.5f)
    }

    private companion object {
        const val ROW = "row"
        const val TRAILING = "trailing"
        val ROW_WIDTH = 300.dp
        val ROW_HEIGHT = 96.dp
        val MARKS = 40.dp
    }
}

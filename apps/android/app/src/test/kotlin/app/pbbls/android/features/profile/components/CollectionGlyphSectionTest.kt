package app.pbbls.android.features.profile.components

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphStroke
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The glyph row speaks as iOS's `CollectionGlyphSection` does: labelled
 * "Glyph", valued with the glyph's name or "None" — not just "Tap to choose".
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CollectionGlyphSectionTest {
    @get:Rule
    val compose = createComposeRule()

    private fun hasState(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    @Test
    fun `the row reads Glyph and None without a glyph`() {
        compose.setContent { PebblesTheme { CollectionGlyphSection(glyph = null, onChoose = {}, onRemove = {}) } }

        compose
            .onNodeWithContentDescription("Glyph")
            .assertHasClickAction()
            .assert(hasState("None"))
    }

    @Test
    fun `the row reads the glyph's name once one is set`() {
        val glyph =
            Glyph(id = "g-1", name = "Wave", strokes = listOf(GlyphStroke(d = "M 0 0 L 10 10", width = 6.0)), viewBox = "0 0 200 200")
        compose.setContent { PebblesTheme { CollectionGlyphSection(glyph = glyph, onChoose = {}, onRemove = {}) } }

        compose
            .onNodeWithContentDescription("Glyph")
            .assertHasClickAction()
            .assert(hasState("Wave"))
    }
}

package app.pbbls.android.navigation

import app.pbbls.android.core.designsystem.PbblsGlyph
import app.pbbls.android.core.designsystem.PbblsGlyphState
import app.pbbls.android.core.designsystem.glyph.GlyphFill
import app.pbbls.android.core.designsystem.glyph.GlyphForm
import app.pbbls.android.core.designsystem.glyph.GlyphTestData
import app.pbbls.android.core.designsystem.glyph.form
import app.pbbls.android.core.designsystem.glyph.forms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The five tabs draw what iOS's `AppTab.icon` draws for the same meaning, and
 * each has the form the bar lights when it is selected (the mirror of iOS
 * `AppTabTests`).
 */
class PebblesNavigationIconTest {
    @Before
    fun installCatalog() = GlyphTestData.install()

    @Test
    fun `each tab draws the icon iOS draws for it`() {
        assertEquals(
            listOf(PbblsGlyph.HOME, PbblsGlyph.PATH, PbblsGlyph.PEOPLE, PbblsGlyph.COLLECTION, PbblsGlyph.PERSON),
            PebblesKey.tabs.map { it.icon() },
        )
    }

    @Test
    fun `every tab has its own glyph`() {
        assertEquals(
            PebblesKey.tabs.size,
            PebblesKey.tabs
                .map { it.icon() }
                .toSet()
                .size,
        )
    }

    @Test
    fun `every tab's selected form differs from regular and is one its glyph has`() {
        PebblesKey.tabs.forEach { tab ->
            assertNotEquals("$tab", GlyphForm.REGULAR, tab.selectedForm())
            assertTrue("$tab", tab.icon().forms.contains(tab.selectedForm()))
        }
    }

    @Test
    fun `people keeps its mark lit on the solid`() {
        assertEquals(GlyphForm(GlyphFill.Solid, marked = true), PebblesKey.People.selectedForm())
    }

    @Test
    fun `the other tabs follow the selected token`() {
        PebblesKey.tabs.filter { it != PebblesKey.People }.forEach { tab ->
            assertEquals("$tab", tab.icon().form(PbblsGlyphState.SELECTED), tab.selectedForm())
        }
    }
}

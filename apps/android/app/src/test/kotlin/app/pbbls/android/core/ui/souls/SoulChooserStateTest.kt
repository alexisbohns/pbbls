package app.pbbls.android.core.ui.souls

import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.SoulWithGlyph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ported case for case from iOS `SoulChooserModelTests`: those lists are foreign vectors. */
class SoulChooserStateTest {
    private val glyph = Glyph(id = "g", strokes = listOf(GlyphStroke(d = "M30,30 L170,170", width = 6.0)), viewBox = "0 0 200 200")

    private fun soul(
        name: String,
        count: Int,
    ) = SoulWithGlyph(id = "id-$name-$count", name = name, glyphId = "g", glyph = glyph, pebblesCount = count)

    private fun names(souls: List<SoulWithGlyph>) = souls.map { it.name }

    private fun state(
        mode: SoulChooserMode,
        souls: List<SoulWithGlyph>,
        selection: List<String> = emptyList(),
    ) = SoulChooserState(mode, souls, selection)

    @Test fun `usual is count desc then name asc, capped at eight, zero counts excluded`() {
        val souls =
            listOf(
                soul("Zoé", 3),
                soul("Adèle", 3),
                soul("Ben", 0),
                soul("Camille", 29),
                soul("Alex", 24),
                soul("Léa", 5),
                soul("Papa", 5),
                soul("Inès", 4),
                soul("Sam", 4),
                soul("Nour", 2),
                soul("Mamie Jo", 2),
            )
        assertEquals(listOf("Camille", "Alex", "Léa", "Papa", "Inès", "Sam", "Adèle", "Zoé"), names(SoulRanking.usual(souls)))
    }

    @Test fun `no usual section when nobody has a pebble`() {
        assertTrue(SoulRanking.usual(listOf(soul("Ben", 0), soul("Ana", 0))).isEmpty())
    }

    @Test fun `sections fold diacritics, keep duplicates, and put non-letters under hash`() {
        val sections =
            SoulRanking.sections(
                listOf(soul("Élise", 1), soul("alex", 2), soul("Alex", 3), soul("1er voisin", 0), soul("Ben", 0)),
            )
        assertEquals(listOf("A", "B", "E", "#"), sections.map { it.letter })
        assertEquals(listOf("alex", "Alex"), names(sections[0].souls))
        assertEquals(listOf("Élise"), names(sections[2].souls))
        assertEquals(listOf("1er voisin"), names(sections[3].souls))
    }

    @Test fun `names that do not start with a letter group under hash, last`() {
        val sections = SoulRanking.sections(listOf(soul("2 Pac", 0), soul("Éa", 0)))
        assertEquals(listOf("E", "#"), sections.map { it.letter })
    }

    @Test fun `folded search matches anywhere, ignoring case and accents`() {
        val souls = listOf(soul("Mathilde", 1), soul("Théo", 2), soul("Mamie Jo", 0))
        assertEquals(listOf("Théo"), names(SoulRanking.results(souls, "theo")))
        assertEquals(listOf("Mamie Jo"), names(SoulRanking.results(souls, "  MIE  ")))
        assertTrue(SoulRanking.results(souls, "").isEmpty())
        val more = listOf(soul("Léa", 1), soul("Adèle", 1), soul("Zed", 1))
        assertEquals(listOf("Adèle", "Léa"), names(SoulRanking.results(more, "le")))
        assertEquals(listOf("Adèle"), names(SoulRanking.results(more, "ADE")))
    }

    @Test fun `add row only without an exact folded match`() {
        val souls = listOf(soul("Théo", 2), soul("Léa", 1))
        assertFalse(SoulRanking.canAdd(souls, "theo"))
        assertFalse(SoulRanking.canAdd(souls, "Théo "))
        assertFalse(SoulRanking.canAdd(souls, "lea"))
        assertTrue(SoulRanking.canAdd(souls, "Le"))
        assertTrue(SoulRanking.canAdd(souls, "Mathilde"))
        assertFalse(SoulRanking.canAdd(souls, "   "))
        val chooser = state(SoulChooserMode.Browse, souls)
        chooser.query = "Mathilde "
        assertEquals("Mathilde", chooser.trimmedQuery)
    }

    @Test fun `match range points at the original characters`() {
        assertEquals(0..1, SoulRanking.matchRange("Maman", "ma"))
        assertEquals(1..2, SoulRanking.matchRange("Léa", "ea"))
        assertNull(SoulRanking.matchRange("Léa", "zz"))
        assertNull(SoulRanking.matchRange("Léa", " "))
    }

    @Test fun `selection toggles in order, the button counts and skips when empty and allowed`() {
        val a = soul("Ana", 1)
        val b = soul("Ben", 1)
        val chooser = state(SoulChooserMode.Pick(allowsSkip = true), listOf(a, b))
        assertEquals(SoulChooserButton.Skip, chooser.button)
        chooser.toggle(b.id)
        chooser.toggle(a.id)
        assertEquals(listOf(b.id, a.id), chooser.selection)
        assertTrue(chooser.isSelected(a.id))
        assertEquals(SoulChooserButton.Done(2), chooser.button)
        chooser.remove(b.id)
        assertEquals(listOf(a.id), chooser.selection)
        chooser.toggle(a.id)
        assertTrue(chooser.selection.isEmpty())
        assertEquals(SoulChooserButton.Skip, chooser.button)
    }

    @Test fun `the edit sheet says Done with nothing chosen, browse has no button`() {
        assertEquals(SoulChooserButton.DoneEmpty, state(SoulChooserMode.Pick(allowsSkip = false), emptyList()).button)
        assertNull(state(SoulChooserMode.Browse, emptyList()).button)
    }

    @Test fun `a created soul lands in the list, chosen in pick mode, and the search closes`() {
        val chooser = state(SoulChooserMode.Pick(allowsSkip = true), listOf(soul("Ben", 0)))
        chooser.openSearch()
        chooser.query = "Mathilde"
        val created = soul("Mathilde", 0)
        chooser.didCreate(created)
        assertTrue(chooser.souls.any { it.name == "Mathilde" })
        assertEquals(listOf(created.id), chooser.selection)
        assertEquals("", chooser.query)
        assertFalse(chooser.isSearching)

        val browse = state(SoulChooserMode.Browse, emptyList())
        browse.didCreate(created)
        assertTrue(browse.selection.isEmpty())
    }

    @Test fun `replacing the souls keeps a selection that still exists and drops one that does not`() {
        val a = soul("Ana", 1)
        val b = soul("Ben", 1)
        val chooser = state(SoulChooserMode.Pick(allowsSkip = true), listOf(a, b), listOf(a.id, b.id))
        chooser.replaceSouls(listOf(a))
        assertEquals(listOf(a.id), chooser.selection)
    }

    @Test fun `didCreate is idempotent, also after replaceSouls already carries the soul`() {
        val created = soul("Mathilde", 0)
        val chooser = state(SoulChooserMode.Pick(allowsSkip = true), emptyList())
        chooser.didCreate(created)
        chooser.didCreate(created)
        assertEquals(1, chooser.souls.size)
        assertEquals(listOf(created.id), chooser.selection)

        val other = state(SoulChooserMode.Pick(allowsSkip = true), emptyList())
        other.replaceSouls(listOf(created))
        other.didCreate(created)
        assertEquals(1, other.souls.size)
        assertEquals(listOf(created.id), other.selection)
    }

    @Test fun `toggle and remove are no-ops in browse`() {
        val a = soul("Ana", 1)
        val chooser = state(SoulChooserMode.Browse, listOf(a), listOf(a.id))
        chooser.toggle("other")
        chooser.remove(a.id)
        assertEquals(listOf(a.id), chooser.selection)
    }

    @Test fun `an unloaded empty list leaves the selection alone, a loaded one prunes it`() {
        val a = soul("Ana", 1)
        val chooser = state(SoulChooserMode.Pick(allowsSkip = true), emptyList(), listOf(a.id))
        chooser.replaceSoulsWhenLoaded(emptyList(), isLoaded = false)
        assertEquals(listOf(a.id), chooser.selection)
        chooser.replaceSoulsWhenLoaded(listOf(a), isLoaded = true)
        assertEquals(listOf(a.id), chooser.selection)
        chooser.replaceSoulsWhenLoaded(emptyList(), isLoaded = true)
        assertTrue(chooser.selection.isEmpty())
    }
}

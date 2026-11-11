package app.pbbls.android.core.designsystem.glyph

import app.pbbls.android.core.designsystem.PbblsGlyph
import app.pbbls.android.core.designsystem.PbblsGlyphState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Android side of the forms contract (spec §4): `packages/icons/glyphs/forms-contract.json`,
 * generated from `formsOf`, `renderForm` and `resolveState`, against the bundled data.
 */
class GlyphFormsContractTest {
    @Serializable
    data class Entry(
        val forms: List<String>,
        val rendered: Map<String, List<RenderedPathData>>,
        val states: Map<String, String>,
    )

    @Serializable
    data class Contract(
        val glyphs: Map<String, Entry>,
    )

    private val contract by lazy {
        val text = GlyphTestData.repoFile("packages/icons/glyphs/forms-contract.json").readText()
        Json { ignoreUnknownKeys = true }.decodeFromString<Contract>(text)
    }

    private fun glyph(name: String): PbblsGlyph = requireNotNull(PbblsGlyph.fromManifestName(name)) { "no PbblsGlyph for $name" }

    @Before
    fun install() = GlyphTestData.install()

    @Test
    fun everySampledGlyphHasTheGeneratorsForms() {
        assertTrue(contract.glyphs.size >= 10)
        for ((name, entry) in contract.glyphs) {
            assertEquals(name, entry.forms, GlyphCatalog.glyph(glyph(name)).formKeys)
        }
    }

    @Test
    fun everyFormRendersAsTheGeneratorRendersIt() {
        for ((name, entry) in contract.glyphs) {
            val data = GlyphCatalog.glyph(glyph(name))
            for ((key, expected) in entry.rendered) {
                val form = GlyphForm.fromKey(key)
                assertNotNull("$name $key", form)
                assertEquals("$name $key", expected, GlyphRenderer.render(data, form!!))
            }
        }
    }

    @Test
    fun everyStateResolvesAsTheGeneratorResolvesIt() {
        for ((name, entry) in contract.glyphs) {
            for ((spec, expected) in entry.states) {
                val parts = spec.split("@")
                val state = PbblsGlyphState.entries.firstOrNull { it.key == parts[0] }
                assertNotNull(spec, state)
                val k = parts.getOrNull(1)?.toIntOrNull() ?: 0
                assertEquals("$name $spec", expected, glyph(name).form(state!!, k).key)
            }
        }
    }

    @Test
    fun progressZeroIsRegularAndAMissingFormFallsBackToRegular() {
        val stack = GlyphCatalog.glyph(PbblsGlyph.STACK)
        assertEquals(GlyphRenderer.render(stack, GlyphForm.REGULAR), GlyphRenderer.render(stack, GlyphForm(GlyphFill.Progress(0))))
        assertEquals(GlyphRenderer.render(stack, GlyphForm.REGULAR), GlyphRenderer.render(stack, GlyphForm.SOLID))
    }

    @Test
    fun everyEntryHasData() {
        for (glyph in PbblsGlyph.entries) {
            assertTrue(glyph.manifestName, GlyphCatalog.glyph(glyph).paths.isNotEmpty())
        }
    }

    @Test
    fun loadsTheWholeSetQuickly() {
        val start = System.nanoTime()
        GlyphCatalog.readIndex { GlyphTestData.resource.inputStream() }
        val ms = (System.nanoTime() - start) / 1_000_000
        println("pbbls_glyphs.json indexed in $ms ms on the JVM")
        assertTrue("$ms ms", ms < 2_000)
    }
}

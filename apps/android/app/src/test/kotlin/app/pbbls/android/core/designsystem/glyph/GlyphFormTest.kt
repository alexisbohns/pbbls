package app.pbbls.android.core.designsystem.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphFormTest {
    private val pairs =
        listOf(
            "regular" to GlyphForm(),
            "marked" to GlyphForm(marked = true),
            "solid" to GlyphForm(GlyphFill.Solid),
            "solid-marked" to GlyphForm(GlyphFill.Solid, marked = true),
            "dotted" to GlyphForm(GlyphFill.Dotted),
            "parts-left+middle" to GlyphForm(GlyphFill.Parts(listOf("left", "middle"))),
            "parts-moon-marked" to GlyphForm(GlyphFill.Parts(listOf("moon")), marked = true),
            "progress-2" to GlyphForm(GlyphFill.Progress(2)),
            "progress-12-marked" to GlyphForm(GlyphFill.Progress(12), marked = true),
        )

    @Test
    fun keysRoundTrip() {
        for ((key, form) in pairs) {
            assertEquals(key, form.key)
            assertEquals(key, form, GlyphForm.fromKey(key))
        }
    }

    @Test
    fun refusesWhatIsNotAFormKey() {
        for (key in listOf(
            "",
            "marked-marked",
            "regular-marked",
            "dotted-marked",
            "progress-0",
            "progress-02",
            "parts-",
            "parts-Moon",
            "parts-a++b",
            "half",
        )) {
            assertNull(key, GlyphForm.fromKey(key))
        }
    }

    @Test
    fun partCombinationsFollowTheGenerator() {
        assertTrue(GlyphData.partCombinations(emptyList()).isEmpty())
        assertTrue(GlyphData.partCombinations(listOf("a")).isEmpty())
        assertEquals(
            listOf(listOf("l"), listOf("m"), listOf("r"), listOf("l", "m"), listOf("l", "r"), listOf("m", "r")),
            GlyphData.partCombinations(listOf("l", "m", "r")),
        )
    }
}

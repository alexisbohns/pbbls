package app.pbbls.android.features.path.valence

import app.pbbls.android.features.path.models.Valence
import app.pbbls.android.features.path.models.ValencePolarity
import app.pbbls.android.features.path.models.ValenceSizeGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ports iOS `ValenceArtTests`. Reads the nine `res/raw/valence_art_*.svg` files
 * straight from disk (the Gradle test task's working directory is the module
 * root, same trick as `LocalizationParityTest` and `WobbleRendererTest` — JVM
 * tests can't reach Android resources), which doubles as an asset regression
 * test: a truncated or re-exported artwork fails here rather than drawing a
 * blob on a device nobody has.
 */
class ValenceArtTest {
    private fun asset(valence: Valence): String {
        val file =
            File("src/main/res/raw/valence_art_${valence.sizeGroup.key}_${valence.polarity.key}.svg")
        check(file.exists()) { "missing valence artwork: ${file.absolutePath}" }
        return file.readText()
    }

    @Test
    fun `every combination resolves to a distinct, non-zero raw resource`() {
        val ids =
            ValenceSizeGroup.entries.flatMap { size ->
                ValencePolarity.entries.map { polarity -> ValenceArtAssets.resId(size, polarity) }
            }
        assertTrue("all raw ids must be non-zero", ids.all { it != 0 })
        assertEquals("all nine raw ids must be distinct", 9, ids.toSet().size)
    }

    @Test
    fun `all nine artworks parse and wobble`() {
        Valence.entries.forEach { valence ->
            val art =
                requireNotNull(ValenceArt.art(valenceAssetKey(valence), asset(valence))) {
                    "valence art failed for $valence"
                }
            assertTrue("$valence has no stroked ink", art.ink.isNotEmpty())
            assertTrue("$valence has a degenerate viewBox", art.viewBox.width > 0 && art.viewBox.height > 0)
            // Exactly one filled path per artwork: the fossil's spiral. Inking
            // it as a centreline instead would fill it in solid, so the split
            // between stroked and filled is the thing worth pinning down.
            assertEquals("$valence should carry one filled region", 1, art.regions.size)
            assertTrue(
                "$valence's region has no contours",
                art.regions
                    .first()
                    .contours
                    .isNotEmpty(),
            )
        }
    }

    @Test
    fun `art is cached per asset, so nine stones cost nine parses`() {
        val key = valenceAssetKey(Valence.NEUTRAL_MEDIUM)
        val markup = asset(Valence.NEUTRAL_MEDIUM)
        assertSame(ValenceArt.art(key, markup), ValenceArt.art(key, markup))
    }

    @Test
    fun `markup with no parseable path degrades to null rather than throwing`() {
        assertNull(ValenceArt.art("empty", "<svg viewBox=\"0 0 10 10\"></svg>"))
        assertNull(ValenceArt.art("no-viewbox", "<svg><path d=\"M0 0 L10 10\" stroke-width=\"2\"/></svg>"))
    }
}

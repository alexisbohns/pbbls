package app.pbbls.android.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `core` / `features` boundary (#851), enforced rather than described.
 *
 * Two rules, and they are not symmetric — the first is absolute, the second is
 * a ratchet:
 *
 * 1. **`core` never imports `features`.** No exceptions, ever. A `core` package
 *    that reaches into a feature is not shared code, it is that feature's code
 *    filed in the wrong place, and it is what makes a later `:core:*` Gradle
 *    module impossible to cut.
 * 2. **A feature never imports another feature**, except for the entries in
 *    [frozenCrossFeatureImports]. That list is frozen at the value #851 left it
 *    and may only shrink: an import that is not on it fails, and an entry on it
 *    that no longer exists fails too. Deleting the last entry deletes the
 *    exception.
 *
 * Scoped to `main` deliberately. A test that exercises one package's logic from
 * another package's fixtures is not an architecture violation, and making the
 * test sources obey the production graph would mean splitting test files to no
 * one's benefit.
 */
class ArchitectureBoundaryTest {
    /**
     * The cross-feature imports that survived #851, each a screen embedding
     * another feature's screen — reachable only by a rewrite (slot parameters
     * or navigation indirection), never by a move. Emptying this list is #914.
     *
     * Format: `<importing package> -> <imported symbol>`.
     */
    private val frozenCrossFeatureImports =
        setOf(
            // `GlyphPickerSheet` hosts glyph's carve and store surfaces inline
            // instead of taking them as slots.
            "$FEATURES.path.create.pickers -> $FEATURES.glyph.carve.GlyphCarveScreen",
            "$FEATURES.path.create.pickers -> $FEATURES.glyph.carve.GlyphCarveViewModel",
            "$FEATURES.path.create.pickers -> $FEATURES.glyph.store.GlyphSwapPanel",
            "$FEATURES.path.create.pickers -> $FEATURES.glyph.store.GlyphTab",
            "$FEATURES.path.create.pickers -> $FEATURES.glyph.store.GlyphTabBar",
            // …and profile reaches across path to get at that same sheet.
            "$FEATURES.profile -> $FEATURES.path.create.pickers.GlyphPickerSheet",
            // Profile's two detail screens open the pebble editor in place.
            "$FEATURES.profile -> $FEATURES.path.EditPebbleScreen",
        )

    @Test
    fun `core never imports features`() {
        val offenders =
            productionFiles()
                .filter { it.packageName == CORE || it.packageName.startsWith("$CORE.") }
                .flatMap { file -> file.featureImports().map { "${file.packageName} -> $it" } }
                .distinct()
                .sorted()

        assertTrue(
            "core must not import features, found:\n" + offenders.joinToString("\n") { "  $it" },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `a feature never imports another feature, beyond the frozen list`() {
        val found =
            productionFiles()
                .filter { it.packageName.startsWith("$FEATURES.") }
                .flatMap { file ->
                    file
                        .featureImports()
                        .filterNot { it.featureOf() == file.packageName.featureOf() }
                        .map { "${file.packageName} -> $it" }
                }.toSortedSet()

        val newlyIntroduced = found - frozenCrossFeatureImports
        assertTrue(
            "new cross-feature imports are not allowed; put the shared code in core/ instead:\n" +
                newlyIntroduced.joinToString("\n") { "  $it" },
            newlyIntroduced.isEmpty(),
        )

        // The other direction: the list is a ratchet, so an entry that has been
        // fixed has to leave it, or the next regression hides behind a stale line.
        val stale = frozenCrossFeatureImports - found
        assertTrue(
            "these frozen exceptions no longer exist — delete them from the list:\n" +
                stale.joinToString("\n") { "  $it" },
            stale.isEmpty(),
        )
    }

    @Test
    fun `the frozen list may shrink, never grow`() {
        // A guard on the guard: the list is editable, and this is the line that
        // makes growing it a deliberate, visible act rather than a quiet +1.
        assertEquals(
            "the frozen cross-feature list is a ratchet — shrink it (#914), never grow it",
            7,
            frozenCrossFeatureImports.size,
        )
    }

    private fun productionFiles() =
        Konsist
            .scopeFromProject(sourceSetName = "main")
            .files
            .map { file ->
                ProductionFile(
                    packageName = file.packagee?.name.orEmpty(),
                    imports = file.imports.map { it.name },
                )
            }

    private data class ProductionFile(
        val packageName: String,
        val imports: List<String>,
    ) {
        fun featureImports() = imports.filter { it.startsWith("$FEATURES.") }
    }

    private companion object {
        const val ROOT = "app.pbbls.android"
        const val CORE = "$ROOT.core"
        const val FEATURES = "$ROOT.features"

        /** `app.pbbls.android.features.path.create.X` -> `path`. */
        fun String.featureOf() = removePrefix("$FEATURES.").substringBefore('.')
    }
}

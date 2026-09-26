package app.pbbls.android.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `core` / `features` boundary (#851), enforced rather than described.
 *
 * Two rules, both absolute:
 *
 * 1. **`core` never imports `features`.** A `core` package that reaches into a
 *    feature is not shared code, it is that feature's code filed in the wrong
 *    place, and it is what makes a later `:core:*` Gradle module impossible to
 *    cut.
 * 2. **A feature never imports another feature.** Shared code goes to `core`.
 *    A screen that hosts another feature's screen takes it as a slot, or
 *    navigates to it, and `navigation/` (which sees every feature) fills the
 *    slot or owns the route. #851 left ten exceptions that no move could reach;
 *    #914 inverted them and deleted the exception list.
 *
 * Scoped to `main` deliberately. A test that exercises one package's logic from
 * another package's fixtures is not an architecture violation, and making the
 * test sources obey the production graph would mean splitting test files to no
 * one's benefit.
 */
class ArchitectureBoundaryTest {
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
    fun `a feature never imports another feature`() {
        val offenders =
            productionFiles()
                .filter { it.packageName.startsWith("$FEATURES.") }
                .flatMap { file ->
                    file
                        .featureImports()
                        .filterNot { it.featureOf() == file.packageName.featureOf() }
                        .map { "${file.packageName} -> $it" }
                }.distinct()
                .sorted()

        assertTrue(
            "features must not import each other; put shared code in core/, or take the other " +
                "feature's screen as a slot that navigation/ fills:\n" +
                offenders.joinToString("\n") { "  $it" },
            offenders.isEmpty(),
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

package app.pbbls.android.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The theme is the only place a colour, a corner radius or a font size is
 * decided (#853). Scoped to `features` and `core.ui`: `core.designsystem` is
 * where the theme itself lives, and `ColorSchemes.kt` is generated.
 * `core.model` is out of scope too — emotion palettes are server data, parsed
 * from hex there.
 */
class ThemeLiteralsTest {
    private val forbidden =
        listOf(
            Regex("""Color\(0x""") to "a Color(0x…) literal — use a MaterialTheme.colorScheme role",
            Regex("""RoundedCornerShape\(\s*[0-9]""") to "a literal corner radius — use MaterialTheme.shapes or CircleShape",
            Regex("""\.copy\([^)]*fontSize""") to "a font-size override — use a MaterialTheme.typography role",
        )

    @Test
    fun `features and core ui decide no colours corners or font sizes`() {
        val offenders =
            Konsist
                .scopeFromProject(sourceSetName = "main")
                .files
                .filter { file ->
                    val pkg = file.packagee?.name.orEmpty()
                    SCANNED.any { pkg == it || pkg.startsWith("$it.") }
                }.flatMap { file ->
                    file.text.lines().withIndex().flatMap { (i, line) ->
                        forbidden.filter { (re, _) -> re.containsMatchIn(line) }.map { (_, why) ->
                            "${file.path.substringAfter("kotlin/")}:${i + 1}: $why"
                        }
                    }
                }

        assertTrue(
            "theme literals outside the theme:\n" + offenders.joinToString("\n") { "  $it" },
            offenders.isEmpty(),
        )
    }

    private companion object {
        const val ROOT = "app.pbbls.android"
        val SCANNED = listOf("$ROOT.features", "$ROOT.core.ui")
    }
}

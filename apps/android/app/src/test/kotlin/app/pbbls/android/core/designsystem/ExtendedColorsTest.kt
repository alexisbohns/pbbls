package app.pbbls.android.core.designsystem

import androidx.compose.material3.lightColorScheme
import app.pbbls.android.testing.AA_TEXT
import app.pbbls.android.testing.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The custom colours and the wallpaper error pin (#990). */
class ExtendedColorsTest {
    private val levels =
        ContrastLevel.entries.flatMap { level ->
            listOf(false, true).map { dark -> Triple("${if (dark) "dark" else "light"}/$level", dark, level) }
        }

    @Test
    fun `the wallpaper pin swaps exactly the four error roles`() {
        // Material's baseline stands in for a wallpaper scheme: red error, purple primary.
        val wallpaper = lightColorScheme()
        val brand = pebblesColorScheme(dark = false, contrast = ContrastLevel.STANDARD)

        val pinned = wallpaper.withErrorFrom(brand)

        assertEquals(brand.error, pinned.error)
        assertEquals(brand.onError, pinned.onError)
        assertEquals(brand.errorContainer, pinned.errorContainer)
        assertEquals(brand.onErrorContainer, pinned.onErrorContainer)
        // ColorScheme has no value equality, so check that the wallpaper's other roles survive one by one.
        assertEquals(wallpaper.primary, pinned.primary)
        assertEquals(wallpaper.secondaryContainer, pinned.secondaryContainer)
        assertEquals(wallpaper.tertiary, pinned.tertiary)
        assertEquals(wallpaper.surface, pinned.surface)
        assertEquals(wallpaper.surfaceContainerHigh, pinned.surfaceContainerHigh)
        assertEquals(wallpaper.outline, pinned.outline)
    }

    @Test
    fun `sand on-colours are AA on their grounds in every scheme`() {
        val failures =
            levels.flatMap { (name, dark, level) ->
                val sand = pebblesSand(dark, level)
                listOf(
                    Triple("onColor/color", sand.onColor, sand.color),
                    Triple("onColorContainer/colorContainer", sand.onColorContainer, sand.colorContainer),
                ).mapNotNull { (pair, ink, ground) ->
                    val ratio = contrastRatio(ink, ground)
                    "$name sand $pair = $ratio".takeIf { ratio < AA_TEXT }
                }
            }
        assertTrue("below AA:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `sand reads as text on every surface step`() {
        val failures =
            levels.flatMap { (name, dark, level) ->
                val scheme = pebblesColorScheme(dark, level)
                val sand = pebblesSand(dark, level).color
                listOf(
                    "surface" to scheme.surface,
                    "surfaceContainerLowest" to scheme.surfaceContainerLowest,
                    "surfaceContainerLow" to scheme.surfaceContainerLow,
                    "surfaceContainer" to scheme.surfaceContainer,
                    "surfaceContainerHigh" to scheme.surfaceContainerHigh,
                    "surfaceContainerHighest" to scheme.surfaceContainerHighest,
                ).mapNotNull { (ground, color) ->
                    val ratio = contrastRatio(sand, color)
                    "$name sand/$ground = $ratio".takeIf { ratio < AA_TEXT }
                }
            }
        assertTrue("below AA:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `extended colours follow mode and contrast`() {
        assertEquals(LightSand, pebblesExtendedColors(dark = false, contrast = ContrastLevel.STANDARD).sand)
        assertEquals(DarkHighContrastSand, pebblesExtendedColors(dark = true, contrast = ContrastLevel.HIGH).sand)
    }
}

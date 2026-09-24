package app.pbbls.android.core.designsystem

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Test

/** One of the 30 Typography roles under test: baseline vs. Pebbles' swapped-face style. */
private data class Quad(
    val name: String,
    val baseline: TextStyle,
    val pebbles: TextStyle,
    val expectedFamily: FontFamily,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class MaterialTypographyTest {
    private val baseline = Typography()
    private val t = PebblesMaterialTypography

    @Test
    fun `display headline and title are ysabeau with lining figures`() {
        listOf(t.displayLarge, t.headlineMedium, t.titleLarge, t.titleMedium, t.titleSmallEmphasized).forEach {
            assertEquals(YsabeauFamily, it.fontFamily)
            assertEquals(YSABEAU_NUMBER_FEATURES, it.fontFeatureSettings)
        }
    }

    @Test
    fun `body and label are inclusive sans`() {
        listOf(t.bodyLarge, t.bodySmall, t.labelLarge, t.labelSmallEmphasized).forEach {
            assertEquals(InclusiveSansFamily, it.fontFamily)
        }
    }

    @Test
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    fun `every role keeps the m3 baseline size with its swapped face`() {
        val roles: List<Quad> =
            listOf(
                Quad("displayLarge", baseline.displayLarge, t.displayLarge, YsabeauFamily),
                Quad("displayMedium", baseline.displayMedium, t.displayMedium, YsabeauFamily),
                Quad("displaySmall", baseline.displaySmall, t.displaySmall, YsabeauFamily),
                Quad("headlineLarge", baseline.headlineLarge, t.headlineLarge, YsabeauFamily),
                Quad("headlineMedium", baseline.headlineMedium, t.headlineMedium, YsabeauFamily),
                Quad("headlineSmall", baseline.headlineSmall, t.headlineSmall, YsabeauFamily),
                Quad("titleLarge", baseline.titleLarge, t.titleLarge, YsabeauFamily),
                Quad("titleMedium", baseline.titleMedium, t.titleMedium, YsabeauFamily),
                Quad("titleSmall", baseline.titleSmall, t.titleSmall, YsabeauFamily),
                Quad("bodyLarge", baseline.bodyLarge, t.bodyLarge, InclusiveSansFamily),
                Quad("bodyMedium", baseline.bodyMedium, t.bodyMedium, InclusiveSansFamily),
                Quad("bodySmall", baseline.bodySmall, t.bodySmall, InclusiveSansFamily),
                Quad("labelLarge", baseline.labelLarge, t.labelLarge, InclusiveSansFamily),
                Quad("labelMedium", baseline.labelMedium, t.labelMedium, InclusiveSansFamily),
                Quad("labelSmall", baseline.labelSmall, t.labelSmall, InclusiveSansFamily),
                Quad("displayLargeEmphasized", baseline.displayLargeEmphasized, t.displayLargeEmphasized, YsabeauFamily),
                Quad("displayMediumEmphasized", baseline.displayMediumEmphasized, t.displayMediumEmphasized, YsabeauFamily),
                Quad("displaySmallEmphasized", baseline.displaySmallEmphasized, t.displaySmallEmphasized, YsabeauFamily),
                Quad("headlineLargeEmphasized", baseline.headlineLargeEmphasized, t.headlineLargeEmphasized, YsabeauFamily),
                Quad("headlineMediumEmphasized", baseline.headlineMediumEmphasized, t.headlineMediumEmphasized, YsabeauFamily),
                Quad("headlineSmallEmphasized", baseline.headlineSmallEmphasized, t.headlineSmallEmphasized, YsabeauFamily),
                Quad("titleLargeEmphasized", baseline.titleLargeEmphasized, t.titleLargeEmphasized, YsabeauFamily),
                Quad("titleMediumEmphasized", baseline.titleMediumEmphasized, t.titleMediumEmphasized, YsabeauFamily),
                Quad("titleSmallEmphasized", baseline.titleSmallEmphasized, t.titleSmallEmphasized, YsabeauFamily),
                Quad("bodyLargeEmphasized", baseline.bodyLargeEmphasized, t.bodyLargeEmphasized, InclusiveSansFamily),
                Quad("bodyMediumEmphasized", baseline.bodyMediumEmphasized, t.bodyMediumEmphasized, InclusiveSansFamily),
                Quad("bodySmallEmphasized", baseline.bodySmallEmphasized, t.bodySmallEmphasized, InclusiveSansFamily),
                Quad("labelLargeEmphasized", baseline.labelLargeEmphasized, t.labelLargeEmphasized, InclusiveSansFamily),
                Quad("labelMediumEmphasized", baseline.labelMediumEmphasized, t.labelMediumEmphasized, InclusiveSansFamily),
                Quad("labelSmallEmphasized", baseline.labelSmallEmphasized, t.labelSmallEmphasized, InclusiveSansFamily),
            )

        roles.forEach { (name, base, pebbles, expectedFamily) ->
            assertEquals("$name fontFamily", expectedFamily, pebbles.fontFamily)
            assertEquals("$name fontSize", base.fontSize, pebbles.fontSize)
            assertEquals("$name lineHeight", base.lineHeight, pebbles.lineHeight)
            assertEquals("$name fontWeight", base.fontWeight, pebbles.fontWeight)
            assertEquals("$name letterSpacing", base.letterSpacing, pebbles.letterSpacing)
        }
    }

    @Test
    fun `hand faces keep their sizes`() {
        assertEquals(22f, PebblesHandTypography.bodyLeadHand.fontSize.value)
        assertEquals(56f, PebblesHandTypography.valenceWord.fontSize.value)
    }
}

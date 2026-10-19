package app.pbbls.android.core.designsystem

import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The regression guard for the weight snapping the Part 2 review caught:
 * `YsabeauFamily` declares four cuts, so a rank at 300 used to render at 400.
 * The axis value is what matters, not the `TextStyle`'s requested weight.
 */
class SpineTypographyTest {
    private fun wghtAxisOf(style: androidx.compose.ui.text.TextStyle): List<Pair<FontStyle, Float>> {
        val family = style.fontFamily as FontListFontFamily
        return family.fonts.map { font ->
            val setting =
                (font as ResourceFont).variationSettings.settings.single { it.axisName == "wght" }
            font.style to setting.toVariationValue(null)
        }
    }

    @Test
    fun ranksPinTheWghtAxisAtTheirExactWeight() {
        val axes = wghtAxisOf(SpineTypography.title)
        assertEquals(2, axes.size)
        assertEquals(setOf(FontStyle.Normal, FontStyle.Italic), axes.map { it.first }.toSet())
        assertEquals(listOf(300f, 300f), axes.map { it.second })
        assertEquals(FontWeight(300), SpineTypography.title.fontWeight)
        assertEquals(listOf(620f, 620f), wghtAxisOf(SpineTypography.soulName).map { it.second })
    }

    @Test
    fun aWeightsFamilyIsMemoised() {
        assertSame(ysabeauAt(300), ysabeauAt(300))
    }
}

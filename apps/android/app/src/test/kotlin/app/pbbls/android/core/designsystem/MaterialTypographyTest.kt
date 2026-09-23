package app.pbbls.android.core.designsystem

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import org.junit.Assert.assertEquals
import org.junit.Test

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
    fun `sizes are the m3 baseline`() {
        assertEquals(baseline.bodyLarge.fontSize, t.bodyLarge.fontSize)
        assertEquals(baseline.headlineMedium.fontSize, t.headlineMedium.fontSize)
        assertEquals(baseline.titleMediumEmphasized.fontWeight, t.titleMediumEmphasized.fontWeight)
    }

    @Test
    fun `hand faces keep their sizes`() {
        assertEquals(22f, PebblesHandTypography.bodyLeadHand.fontSize.value)
        assertEquals(56f, PebblesHandTypography.valenceWord.fontSize.value)
    }
}

package app.pbbls.android.theme

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression guard for the hand-transcribed type ramp (plan: 18 tokens, exact size/weight/tracking). */
class TypographyTest {
    @Test
    fun bodyMatchesSpec() {
        assertEquals(17.sp, PebblesTypography.body.fontSize)
        assertEquals(FontWeight.Normal, PebblesTypography.body.fontWeight)
        assertEquals(0.02f.em, PebblesTypography.body.letterSpacing)
    }

    @Test
    fun headlineEmphasizedMatchesSpec() {
        assertEquals(17.sp, PebblesTypography.headlineEmphasized.fontSize)
        assertEquals(FontWeight.Bold, PebblesTypography.headlineEmphasized.fontWeight)
    }

    @Test
    fun metaTokensAreMarkedUppercase() {
        assertTrue(PebblesTypography.meta in PebblesTypography.uppercaseTokens)
        assertTrue(PebblesTypography.metaEmphasized in PebblesTypography.uppercaseTokens)
        assertTrue(PebblesTypography.cardHeading in PebblesTypography.uppercaseTokens)
        assertTrue(PebblesTypography.cardHeadingEmphasized in PebblesTypography.uppercaseTokens)
    }

    @Test
    fun bodyIsNotMarkedUppercase() {
        assertTrue(PebblesTypography.body !in PebblesTypography.uppercaseTokens)
    }

    @Test
    fun titleUsesNegativeTracking() {
        assertEquals(28.sp, PebblesTypography.title.fontSize)
        assertEquals((-0.02f).em, PebblesTypography.title.letterSpacing)
        assertEquals("pnum, lnum", PebblesTypography.title.fontFeatureSettings)
    }

    @Test
    fun handTokensUseReenieBeanieSizes() {
        assertEquals(22.sp, PebblesTypography.bodyLeadHand.fontSize)
        assertEquals(41.sp, PebblesTypography.largeTitleHand.fontSize)
    }
}

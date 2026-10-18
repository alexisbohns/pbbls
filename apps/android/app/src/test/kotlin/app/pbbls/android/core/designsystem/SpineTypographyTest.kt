package app.pbbls.android.core.designsystem

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class SpineTypographyTest {
    @Test
    fun ranksKeepTheirExactWeight() {
        assertEquals(FontWeight(300), SpineTypography.title.fontWeight)
    }

    @Test
    fun differentWeightsResolveDifferentFamilies() {
        assertNotSame(SpineTypography.title.fontFamily, SpineTypography.kicker.fontFamily)
    }

    @Test
    fun aWeightsFamilyIsMemoised() {
        assertSame(ysabeauAt(300), ysabeauAt(300))
    }
}

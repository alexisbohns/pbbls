package app.pbbls.android.core.designsystem

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

/** The achievements board's ranks (#1307), so features/achievements never names a size (ThemeLiteralsTest). */
class AchievementTypeRanksTest {
    @Test
    fun `the ranks carry the board's sizes`() {
        assertEquals(78.sp, SpineTypography.daysKept.fontSize)
        assertEquals(11.5.sp, SpineTypography.cardBand.fontSize)
        assertEquals(FontStyle.Italic, SpineTypography.cardHint.fontStyle)
        assertEquals(21.sp, SpineTypography.milestone.fontSize)
        assertEquals(9.sp, SpineTypography.discNumeral.fontSize)
        assertEquals("smcp, c2sc", SpineTypography.tableHead.fontFeatureSettings)
    }
}

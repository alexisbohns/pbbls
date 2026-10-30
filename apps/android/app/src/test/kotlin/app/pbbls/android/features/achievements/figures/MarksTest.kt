package app.pbbls.android.features.achievements.figures

import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.model.achievements.StoneKind
import app.pbbls.android.core.ui.render.stone.InkPrint
import org.junit.Assert.assertEquals
import org.junit.Test

/** The board's six-firsts table (`app.js`, the wheel's six ring), one entry per kind. */
class MarksTest {
    @Test
    fun `intensities are solid on the neutral outline at three sizes, valences the three prints at medium`() {
        assertEquals(
            SixMark(InkSeal(ValenceSizeGroup.SMALL, InkPrint.RELIEF, ValencePolarity.NEUTRAL), 9f),
            SIX_MARKS.getValue(StoneKind.SMALL),
        )
        assertEquals(
            SixMark(InkSeal(ValenceSizeGroup.MEDIUM, InkPrint.RELIEF, ValencePolarity.NEUTRAL), 12f),
            SIX_MARKS.getValue(StoneKind.MEDIUM),
        )
        assertEquals(
            SixMark(InkSeal(ValenceSizeGroup.LARGE, InkPrint.RELIEF, ValencePolarity.NEUTRAL), 14f),
            SIX_MARKS.getValue(StoneKind.LARGE),
        )
        assertEquals(SixMark(InkSeal(ValenceSizeGroup.MEDIUM, InkPrint.ENGRAVED), 12.5f), SIX_MARKS.getValue(StoneKind.LOWLIGHT))
        assertEquals(SixMark(InkSeal(ValenceSizeGroup.MEDIUM, InkPrint.OPEN), 12.5f), SIX_MARKS.getValue(StoneKind.NEUTRAL))
        assertEquals(SixMark(InkSeal(ValenceSizeGroup.MEDIUM, InkPrint.RELIEF), 12.5f), SIX_MARKS.getValue(StoneKind.HIGHLIGHT))
        assertEquals(StoneKind.entries.toSet(), SIX_MARKS.keys)
    }

    @Test
    fun `an emotion's seal grows with its intensity`() {
        assertEquals(9f, emotionSealWidth(1), 1e-4f)
        assertEquals(10.6f, emotionSealWidth(2), 1e-4f)
        assertEquals(12.2f, emotionSealWidth(3), 1e-4f)
    }
}

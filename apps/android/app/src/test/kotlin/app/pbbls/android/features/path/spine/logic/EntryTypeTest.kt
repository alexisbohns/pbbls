package app.pbbls.android.features.path.spine.logic

import app.pbbls.android.core.model.ValencePolarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** The web's `entry-type.test.ts`, its phone expectations (spec D4: no wide spread on Android). */
class EntryTypeTest {
    private val intensities = listOf(1, 2, 3)
    private val polarities = ValencePolarity.entries

    private val sizes = mapOf(1 to 19f, 2 to 24f, 3 to 31f)
    private val cuts =
        mapOf(
            ValencePolarity.HIGHLIGHT to Triple(600, false, -0.012f),
            ValencePolarity.NEUTRAL to Triple(460, false, -0.012f),
            ValencePolarity.LOWLIGHT to Triple(340, true, -0.004f),
        )

    @Test
    fun `sets every intensity and cut on phone`() {
        for (intensity in intensities) {
            for (polarity in polarities) {
                val (weight, italic, tracking) = cuts.getValue(polarity)
                val size = sizes.getValue(intensity)
                assertEquals(
                    EntryType(
                        name = NameType(fontSizeSp = size, fontWeight = weight, italic = italic, letterSpacingEm = tracking),
                        lineHeight = 1.12f,
                        anchorDp = size * 0.6f,
                    ),
                    EntryTypes.entry(intensity, polarity),
                )
            }
        }
    }

    @Test
    fun `grows strictly with intensity, so size alone tells it`() {
        val sizes = intensities.map { EntryTypes.entry(it, ValencePolarity.NEUTRAL).name.fontSizeSp }
        assertEquals(sizes.sorted(), sizes)
        assertEquals(3, sizes.toSet().size)
    }

    @Test
    fun `refuses an intensity off the 1 to 3 scale`() {
        assertThrows(IllegalArgumentException::class.java) { EntryTypes.entry(0, ValencePolarity.NEUTRAL) }
        assertThrows(IllegalArgumentException::class.java) { EntryTypes.contentsName(4, ValencePolarity.NEUTRAL) }
    }

    @Test
    fun `weighs contents by intensity and sets lowlights in a light italic`() {
        assertEquals(listOf(400, 470, 620), intensities.map { EntryTypes.contentsName(it, ValencePolarity.HIGHLIGHT).fontWeight })
        assertEquals(listOf(400, 470, 620), intensities.map { EntryTypes.contentsName(it, ValencePolarity.NEUTRAL).fontWeight })
        for (intensity in intensities) {
            val type = EntryTypes.contentsName(intensity, ValencePolarity.LOWLIGHT)
            assertEquals(350, type.fontWeight)
            assertEquals(true, type.italic)
        }
    }

    @Test
    fun `sets contents names at sixteen and a half with no tracking`() {
        val type = EntryTypes.contentsName(2, ValencePolarity.NEUTRAL)
        assertEquals(16.5f, type.fontSizeSp)
        assertEquals(0f, type.letterSpacingEm)
    }

    @Test
    fun `cuts a card title by valence`() {
        assertEquals(600 to false, EntryTypes.cardTitleCut(ValencePolarity.HIGHLIGHT))
        assertEquals(480 to false, EntryTypes.cardTitleCut(ValencePolarity.NEUTRAL))
        assertEquals(360 to true, EntryTypes.cardTitleCut(ValencePolarity.LOWLIGHT))
    }
}

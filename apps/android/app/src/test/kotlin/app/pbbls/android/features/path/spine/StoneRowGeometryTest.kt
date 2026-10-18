package app.pbbls.android.features.path.spine

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.ui.render.stone.StoneGeometry
import app.pbbls.android.features.path.spine.logic.EntryTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pebble row's stone and hit box over every valence's stone at the card
 * anchor and the three entry anchors (spec §The feed, §Accessibility): the stone's
 * centre is the anchor on the line, the hit box encloses it by 10 dp or reaches
 * 48 dp, and the row's minimum height is the web's `anchor + stoneH/2 + 14`.
 */
class StoneRowGeometryTest {
    private class Case(
        val label: String,
        val anchor: Dp,
        val stoneWidth: Dp,
        val stoneHeight: Dp,
    )

    private val cases: List<Case> =
        Valence.entries.flatMap { valence ->
            val size = valence.sizeGroup
            val width = StoneGeometry.STONE_WIDTH_DP.getValue(size).dp
            val box = StoneGeometry.outlineBox(size)
            val height = width * (box.height / box.width)
            val anchors =
                listOf("card" to SpineLayout.CARD_ANCHOR) +
                    (1..3).map { intensity -> "entry $intensity" to EntryTypes.entry(intensity, valence.polarity).anchorDp.dp }
            anchors.map { (name, anchor) -> Case("$valence on $name", anchor, width, height) }
        }

    private fun assertDp(
        message: String,
        expected: Dp,
        actual: Dp,
    ) = assertEquals(message, expected.value, actual.value, 0.001f)

    @Test
    fun `thirty-six cases`() {
        assertEquals(36, cases.size)
    }

    @Test
    fun `the stone's centre is the anchor on the line`() {
        cases.forEach { case ->
            val g = stoneRowGeometry(case.anchor, case.stoneWidth, case.stoneHeight)
            assertDp("${case.label}: stone centre x", SpineLayout.SPINE_X, g.stoneLeft + case.stoneWidth / 2)
            assertDp("${case.label}: stone centre y", case.anchor, g.stoneTop + case.stoneHeight / 2)
        }
    }

    @Test
    fun `the hit box is centred on the stone and encloses it by ten dp, or reaches forty-eight`() {
        cases.forEach { case ->
            val g = stoneRowGeometry(case.anchor, case.stoneWidth, case.stoneHeight)
            assertDp("${case.label}: hit centre x", SpineLayout.SPINE_X, g.hitLeft + g.hitWidth / 2)
            assertDp("${case.label}: hit centre y", case.anchor, g.hitTop + g.hitHeight / 2)
            assertTrue("${case.label}: hit width ${g.hitWidth}", g.hitWidth >= case.stoneWidth + 20.dp && g.hitWidth >= 48.dp)
            assertTrue("${case.label}: hit height ${g.hitHeight}", g.hitHeight >= case.stoneHeight + 20.dp && g.hitHeight >= 48.dp)
            // Exactly one of the two rules binds: the stone plus 10 dp, or the 48 dp floor.
            val widthRule = case.stoneWidth + 20.dp
            val heightRule = case.stoneHeight + 20.dp
            assertDp("${case.label}: hit width rule", if (widthRule >= 48.dp) widthRule else 48.dp, g.hitWidth)
            assertDp("${case.label}: hit height rule", if (heightRule >= 48.dp) heightRule else 48.dp, g.hitHeight)
        }
    }

    @Test
    fun `the row's minimum height is the web's formula`() {
        cases.forEach { case ->
            val g = stoneRowGeometry(case.anchor, case.stoneWidth, case.stoneHeight)
            assertDp("${case.label}: min height", case.anchor + case.stoneHeight / 2 + 14.dp, g.minHeight)
        }
    }

    @Test
    fun `on its own entry every stone overhangs the row's top, on a card only the large one`() {
        Valence.entries.forEach { valence ->
            val size = valence.sizeGroup
            val width = StoneGeometry.STONE_WIDTH_DP.getValue(size).dp
            val box = StoneGeometry.outlineBox(size)
            val height = width * (box.height / box.width)
            val entry = stoneRowGeometry(EntryTypes.entry(valence.intensity, valence.polarity).anchorDp.dp, width, height)
            assertTrue("$valence on its entry: stoneTop ${entry.stoneTop}", entry.stoneTop < 0.dp)
            val card = stoneRowGeometry(SpineLayout.CARD_ANCHOR, width, height)
            assertEquals("$valence on a card: stoneTop ${card.stoneTop}", height > SpineLayout.CARD_ANCHOR * 2, card.stoneTop < 0.dp)
        }
    }
}

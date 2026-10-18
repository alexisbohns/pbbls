package app.pbbls.android.core.ui.render.stone

import app.pbbls.android.core.common.UuidHash
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `stone-recipe.test.ts` transcribed, plus the web's verbatim numbers for the
 * fixture seed (printed once from `apps/web` with
 * `npx tsx -e 'import { stoneRecipe } from "./lib/utils/spine/stone-recipe"; …'`).
 */
class StoneRecipeTest {
    private val seed = UuidHash.fnv1a("0e9d8c7b-6a5f-4e3d-8c2b-1a0f9e8d7c6b")

    private fun gem(size: ValenceSizeGroup): StoneRecipe.Gem {
        val recipe = StoneRecipes.of(size, ValencePolarity.HIGHLIGHT, seed)
        assertTrue("expected a gem", recipe is StoneRecipe.Gem)
        return recipe as StoneRecipe.Gem
    }

    private fun assertEllipse(
        expected: StoneEllipse,
        actual: StoneEllipse,
    ) {
        assertEquals(expected.cx, actual.cx, 1e-4f)
        assertEquals(expected.cy, actual.cy, 1e-4f)
        assertEquals(expected.rx, actual.rx, 1e-4f)
        assertEquals(expected.ry, actual.ry, 1e-4f)
        assertEquals(expected.rotate, actual.rotate, 0f)
        assertEquals(expected.opacity, actual.opacity, 0f)
    }

    @Test
    fun `the seed is the web's hashUUID`() {
        assertEquals(3118864542L, seed)
    }

    @Test
    fun `mulberry32 matches JS for seed 1`() {
        val rand = StoneRecipes.random(1L)
        assertEquals(0.6270739405881613, rand(), 0.0)
        assertEquals(0.002735721180215478, rand(), 0.0)
        assertEquals(0.5274470399599522, rand(), 0.0)
    }

    @Test
    fun `draws the same stone for the same seed`() {
        assertEquals(
            StoneRecipes.of(ValenceSizeGroup.MEDIUM, ValencePolarity.HIGHLIGHT, seed),
            StoneRecipes.of(ValenceSizeGroup.MEDIUM, ValencePolarity.HIGHLIGHT, seed),
        )
    }

    @Test
    fun `draws a different stone for another pebble`() {
        val other = UuidHash.fnv1a("8d1f6c3e-2b7a-4c1e-9f0a-5e6d7c8b9a01")
        assertNotEquals(
            StoneRecipes.of(ValenceSizeGroup.MEDIUM, ValencePolarity.LOWLIGHT, seed),
            StoneRecipes.of(ValenceSizeGroup.MEDIUM, ValencePolarity.LOWLIGHT, other),
        )
    }

    @Test
    fun `cuts a gem into five facets and a face, with ten glitter dots and two sparkles`() {
        val gem = gem(ValenceSizeGroup.LARGE)
        assertEquals(6, gem.facets.size)
        assertTrue(gem.facets.take(5).all { it.points.size == 4 && it.edge != null })
        val face = gem.facets[5]
        assertNull(face.edge)
        assertEquals(0.14f, face.opacity, 0f)
        assertEquals(5, face.points.size)
        assertEquals(10, gem.dots.size)
        assertTrue(gem.dots.all { it.slot == StoneSlot.LIGHT })
        assertEquals(2, gem.sparkles.size)
    }

    @Test
    fun `shades the facets from the upper-left light - lit, lit, dark, dark, lit`() {
        val gem = gem(ValenceSizeGroup.MEDIUM)
        assertEquals(
            listOf(StoneSlot.WHITE, StoneSlot.WHITE, StoneSlot.DARK, StoneSlot.DARK, StoneSlot.WHITE),
            gem.facets.take(5).map { it.slot },
        )
    }

    @Test
    fun `lands the medium gem where the web does`() {
        val gem = gem(ValenceSizeGroup.MEDIUM)
        assertEquals(StoneDot(179.39f, 207.06f, 4.26f, 0.93f, StoneSlot.LIGHT), gem.dots[0])
        assertEquals(StoneDot(168.84f, 206.72f, 3.96f, 0.82f, StoneSlot.LIGHT), gem.dots[9])
        assertEquals(
            listOf(84.57f to 59.84f, 155.58f to 45.03f, 294.13f to -182.52f, -131.93f to -93.66f),
            gem.facets[0].points,
        )
        assertEquals((84.57f to 59.84f) to (-131.93f to -93.66f), gem.facets[0].edge)
        assertEquals(
            listOf(84.57f to 59.84f, 155.58f to 45.03f, 194.3f to 91.62f, 137.44f to 143.24f, 67.46f to 112.97f),
            gem.facets[5].points,
        )
        assertEquals(
            "M85.8 44.14Q88.31 57.29 101.46 59.8Q88.31 62.31 85.8 75.46Q83.29 62.31 70.14 59.8Q83.29 57.29 85.8 44.14Z",
            gem.sparkles[0],
        )
        assertEquals(
            "M163.8 68.84Q165.27 76.53 172.96 78Q165.27 79.47 163.8 87.16Q162.33 79.47 154.64 78Q162.33 76.53 163.8 68.84Z",
            gem.sparkles[1],
        )
    }

    @Test
    fun `grains a river pebble with fourteen specks, lit and shaded`() {
        val recipe = StoneRecipes.of(ValenceSizeGroup.SMALL, ValencePolarity.NEUTRAL, seed)
        assertTrue(recipe is StoneRecipe.Pebble)
        assertEquals(14, recipe.dots.size)
        assertTrue(recipe.dots.all { it.slot == StoneSlot.LIGHT || it.slot == StoneSlot.SHADED })
        assertEquals(StoneDot(177.45f, 168.99f, 3.6f, 0.53f, StoneSlot.LIGHT), recipe.dots[0])
        assertEquals(StoneDot(73.04f, 48.03f, 3.12f, 0.37f, StoneSlot.SHADED), recipe.dots[2])
        assertEllipse(StoneEllipse(95f, 60f, 85f, 40f, -16f, 0.16f), (recipe as StoneRecipe.Pebble).light)
    }

    @Test
    fun `pits lava nine times in its dark`() {
        val recipe = StoneRecipes.of(ValenceSizeGroup.MEDIUM, ValencePolarity.LOWLIGHT, seed)
        assertTrue(recipe is StoneRecipe.Lava)
        assertEquals(9, recipe.dots.size)
        assertTrue(recipe.dots.all { it.slot == StoneSlot.DARK })
        assertEquals(StoneDot(175.94f, 197.14f, 4.83f, 0.59f, StoneSlot.DARK), recipe.dots[0])
        assertEquals(StoneDot(162.01f, 172.48f, 4.22f, 0.36f, StoneSlot.DARK), recipe.dots[1])
        assertEllipse(StoneEllipse(88.4f, 62.4f, 67.6f, 39f, 0f, 0.07f), (recipe as StoneRecipe.Lava).light)
    }

    @Test
    fun `keeps every dot inside the canvas and its radius the same size on screen`() {
        for ((size, width, px) in listOf(Triple(ValenceSizeGroup.SMALL, 250f, 34f), Triple(ValenceSizeGroup.LARGE, 260f, 54f))) {
            for (d in StoneRecipes.of(size, ValencePolarity.LOWLIGHT, seed).dots) {
                assertTrue("$size cx ${d.cx}", d.cx > 0f && d.cx < width)
                val onScreen = d.r * px / width
                assertTrue("$size r $onScreen", onScreen >= 0.4f - 0.01f && onScreen <= 0.99f + 0.01f)
            }
        }
    }

    @Test
    fun `glint matches the mock's medium glint`() {
        val glint = StoneRecipes.glint(ValenceSizeGroup.MEDIUM)
        assertEquals(98.8f, glint.sheen.cx, 1e-4f)
        assertEquals(78f, glint.sheen.cy, 1e-4f)
        assertEquals(78f, glint.sheen.rx, 1e-4f)
        assertEquals(-24f, glint.sheen.rotate, 0f)
        assertEquals(416f, glint.travel, 1e-4f)
        assertEquals(-143f, glint.sweep.x, 1e-4f)
        assertEquals(-104f, glint.sweep.y, 1e-4f)
        assertEquals(468f, glint.sweep.height, 1e-4f)
    }

    @Test
    fun `starPath draws the mock's four-point spark`() {
        assertEquals(
            "M82.5 28.75Q85.9 46.6 103.75 50Q85.9 53.4 82.5 71.25Q79.1 53.4 61.25 50Q79.1 46.6 82.5 28.75Z",
            StoneRecipes.starPath(82.5f, 50f, 21.25f),
        )
    }

    @Test
    fun `motion tables carry the web's reach and open delay`() {
        assertEquals(7.2f, StoneMotion.RIPPLE_REACH.getValue(ValenceSizeGroup.SMALL), 0f)
        assertEquals(5.6f, StoneMotion.RIPPLE_REACH.getValue(ValenceSizeGroup.MEDIUM), 0f)
        assertEquals(4.4f, StoneMotion.RIPPLE_REACH.getValue(ValenceSizeGroup.LARGE), 0f)
        assertEquals(360L, StoneMotion.OPEN_DELAY_MS)
        assertEquals(520, StoneMotion.Dip.DURATION)
        assertEquals(1650 + 2 * 170, StoneMotion.Wave.duration(2))
        assertEquals(120 + 2 * 230, StoneMotion.Wave.delay(2))
    }
}

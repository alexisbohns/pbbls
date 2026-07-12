package app.pbbls.android.features.path.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parity guard for the client-side [EmotionCategoryOrdering] table (D14, ported
 * verbatim from iOS). Pins the full 9-key × 7-slug map against the exact iOS
 * sequences, checks every cell is a permutation of the seven category slugs, and
 * exercises the [EmotionCategoryOrdering.order] lookup + null fallback.
 */
class EmotionCategoryOrderingTest {
    private val allCategorySlugs =
        setOf("anger", "fear", "joy", "peace", "pride", "sadness", "shame")

    @Test
    fun `the table matches the iOS nine keys by seven slugs`() {
        val expected =
            mapOf(
                Pair(ValenceSizeGroup.LARGE, ValencePolarity.HIGHLIGHT) to
                    listOf("pride", "joy", "peace", "fear", "anger", "shame", "sadness"),
                Pair(ValenceSizeGroup.MEDIUM, ValencePolarity.HIGHLIGHT) to
                    listOf("joy", "pride", "peace", "fear", "anger", "shame", "sadness"),
                Pair(ValenceSizeGroup.SMALL, ValencePolarity.HIGHLIGHT) to
                    listOf("peace", "joy", "pride", "shame", "sadness", "fear", "anger"),
                Pair(ValenceSizeGroup.LARGE, ValencePolarity.NEUTRAL) to
                    listOf("peace", "joy", "pride", "fear", "anger", "shame", "sadness"),
                Pair(ValenceSizeGroup.MEDIUM, ValencePolarity.NEUTRAL) to
                    listOf("peace", "fear", "joy", "anger", "pride", "shame", "sadness"),
                Pair(ValenceSizeGroup.SMALL, ValencePolarity.NEUTRAL) to
                    listOf("peace", "anger", "joy", "fear", "pride", "sadness", "shame"),
                Pair(ValenceSizeGroup.LARGE, ValencePolarity.LOWLIGHT) to
                    listOf("sadness", "fear", "anger", "shame", "peace", "joy", "pride"),
                Pair(ValenceSizeGroup.MEDIUM, ValencePolarity.LOWLIGHT) to
                    listOf("anger", "fear", "shame", "sadness", "peace", "pride", "joy"),
                Pair(ValenceSizeGroup.SMALL, ValencePolarity.LOWLIGHT) to
                    listOf("shame", "sadness", "fear", "anger", "peace", "pride", "joy"),
            )
        assertEquals(expected, EmotionCategoryOrdering.byValence)
    }

    @Test
    fun `every key maps to exactly seven category slugs`() {
        assertEquals(9, EmotionCategoryOrdering.byValence.size)
        for ((key, slugs) in EmotionCategoryOrdering.byValence) {
            assertEquals("$key should list 7 slugs", 7, slugs.size)
            assertEquals("$key slug set mismatch", allCategorySlugs, slugs.toSet())
        }
    }

    @Test
    fun `the default equals medium neutral and drives the null lookup`() {
        assertEquals(
            listOf("peace", "fear", "joy", "anger", "pride", "shame", "sadness"),
            EmotionCategoryOrdering.default,
        )
        assertEquals(EmotionCategoryOrdering.default, EmotionCategoryOrdering.order(null))
        assertEquals(
            EmotionCategoryOrdering.byValence[Pair(ValenceSizeGroup.MEDIUM, ValencePolarity.NEUTRAL)],
            EmotionCategoryOrdering.order(null),
        )
    }

    @Test
    fun `order looks up the cell for a valence`() {
        assertEquals(
            listOf("pride", "joy", "peace", "fear", "anger", "shame", "sadness"),
            EmotionCategoryOrdering.order(Valence.HIGHLIGHT_LARGE),
        )
        assertEquals(
            listOf("anger", "fear", "shame", "sadness", "peace", "pride", "joy"),
            EmotionCategoryOrdering.order(Valence.LOWLIGHT_MEDIUM),
        )
    }

    @Test
    fun `cells are individually mapped, not a blanket default`() {
        val orderings = Valence.entries.map { EmotionCategoryOrdering.order(it) }
        assertTrue(orderings.any { it != EmotionCategoryOrdering.default })
        assertTrue(orderings.toSet().size > 1)
    }
}

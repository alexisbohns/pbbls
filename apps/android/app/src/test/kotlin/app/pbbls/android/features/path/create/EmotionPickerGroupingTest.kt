package app.pbbls.android.features.path.create

import app.pbbls.android.features.path.models.EmotionCategoryOrdering
import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.EmotionWithPalette
import app.pbbls.android.features.path.models.Valence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure [EmotionPickerGrouping] that backs the emotion picker (D14):
 * categories are ordered by [EmotionCategoryOrdering] for the selected valence
 * (falling back to MEDIUM · NEUTRAL for none), empty categories are dropped,
 * unknown categories sink to the end, and each group's rows stay within its
 * category, name-sorted. Pure JVM — no Android runtime.
 */
class EmotionPickerGroupingTest {
    private val palette: EmotionPalette =
        requireNotNull(
            EmotionPalette.fromHex(
                primaryHex = "#7B5E99FF",
                secondaryHex = "#AE91CCFF",
                lightHex = "#F2EFF5FF",
                surfaceHex = "#7B5E991A",
                darkHex = "#2A2138FF",
            ),
        )

    private fun emotion(
        id: String,
        slug: String,
        name: String,
        categorySlug: String,
        categoryName: String,
    ): EmotionWithPalette =
        EmotionWithPalette(
            id = id,
            slug = slug,
            name = name,
            emoji = "🙂",
            categoryId = "cat-$categorySlug",
            categorySlug = categorySlug,
            categoryName = categoryName,
            palette = palette,
        )

    // Three categories (joy, pride, peace), with joy carrying two emotions.
    private val rows =
        listOf(
            emotion("joy-1", "joyful", "Joyful", "joy", "Joy"),
            emotion("joy-2", "cheerful", "Cheerful", "joy", "Joy"),
            emotion("pride-1", "proud", "Proud", "pride", "Pride"),
            emotion("peace-1", "peaceful", "Peaceful", "peace", "Peace"),
        )

    @Test
    fun `orders present categories by the valence table`() {
        val groups = EmotionPickerGrouping.groups(rows, Valence.HIGHLIGHT_LARGE)
        // HIGHLIGHT_LARGE full order is pride, joy, peace, fear, anger, shame, sadness.
        assertEquals(listOf("pride", "joy", "peace"), groups.map { it.categorySlug })
    }

    @Test
    fun `falls back to the medium-neutral order when no valence is selected`() {
        val groups = EmotionPickerGrouping.groups(rows, null)
        assertEquals(listOf("peace", "joy", "pride"), groups.map { it.categorySlug })

        val present = groups.map { it.categorySlug }.toSet()
        assertEquals(
            EmotionCategoryOrdering.order(null).filter { it in present },
            groups.map { it.categorySlug },
        )
    }

    @Test
    fun `omits empty categories and keeps each group's rows within its category`() {
        val groups = EmotionPickerGrouping.groups(rows, Valence.HIGHLIGHT_LARGE)
        assertEquals(3, groups.size)
        for (group in groups) {
            assertTrue(group.rows.isNotEmpty())
            assertTrue(group.rows.all { it.categorySlug == group.categorySlug })
        }
    }

    @Test
    fun `sorts the rows inside a category by name`() {
        val groups = EmotionPickerGrouping.groups(rows, Valence.HIGHLIGHT_LARGE)
        val joy = groups.first { it.categorySlug == "joy" }
        assertEquals(listOf("Cheerful", "Joyful"), joy.rows.map { it.name })
    }

    @Test
    fun `carries the category name and palette from the first row`() {
        val groups = EmotionPickerGrouping.groups(rows, Valence.HIGHLIGHT_LARGE)
        val joy = groups.first { it.categorySlug == "joy" }
        assertEquals("Joy", joy.categoryName)
        assertEquals(palette, joy.palette)
    }

    @Test
    fun `sends categories absent from the ordering table to the end`() {
        val withUnknown = rows + emotion("cur-1", "curious", "Curious", "curiosity", "Curiosity")
        val groups = EmotionPickerGrouping.groups(withUnknown, Valence.HIGHLIGHT_LARGE)
        assertEquals(listOf("pride", "joy", "peace", "curiosity"), groups.map { it.categorySlug })
    }
}

package app.pbbls.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EmotionFamiliesTest {
    private val palette: EmotionPalette =
        requireNotNull(
            EmotionPalette.fromHex(
                primaryHex = "#7B5E99FF",
                secondaryHex = "#AE91CCFF",
                lightHex = "#F2EFF5FF",
                surfaceHex = "#7B5E991A",
                darkHex = "#2A2138FF",
                shadedHex = "#4A3A5CFF",
            ),
        )

    private fun row(
        slug: String,
        category: String,
    ) = EmotionWithPalette(slug, slug, slug, "🙂", "cat-$category", category, category.replaceFirstChar { it.uppercase() }, palette)

    @Test
    fun `one family per category, in the wheel's order, unknown families last`() {
        val families =
            EmotionFamilies.of(
                listOf(row("calm", "peace"), row("happy", "joy"), row("content", "peace"), row("awe", "wonder"), row("brave", "pride")),
            )
        assertEquals(listOf("joy", "pride", "peace", "wonder"), families.map { it.slug })
        assertEquals("Peace", families[2].name)
    }
}

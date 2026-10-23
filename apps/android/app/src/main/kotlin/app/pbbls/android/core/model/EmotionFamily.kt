package app.pbbls.android.core.model

/**
 * An emotion family: the database's `emotion_categories` row as the wraps see it (#1207). The
 * wraps name families by [slug]; the name and the palette come from `v_emotions_with_palette`,
 * whose rows carry their category's columns.
 */
data class EmotionFamily(
    val slug: String,
    val name: String,
    val palette: EmotionPalette,
)

object EmotionFamilies {
    /** The gem's clockwise order (the method's wheel): the bright families first, the hard ones below. */
    val WHEEL: List<String> = listOf("joy", "pride", "anger", "fear", "shame", "sadness", "peace")

    /**
     * One family per category slug, its name and palette from its first emotion, in [WHEEL] order and
     * then by slug for any family the wheel does not list yet.
     */
    fun of(rows: Iterable<EmotionWithPalette>): List<EmotionFamily> =
        rows
            .distinctBy { it.categorySlug }
            .map { EmotionFamily(it.categorySlug, it.categoryName, it.palette) }
            .sortedWith(compareBy({ WHEEL.indexOf(it.slug).let { i -> if (i < 0) WHEEL.size else i } }, { it.slug }))
}

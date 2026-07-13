package app.pbbls.android.features.path.create

import app.pbbls.android.features.path.models.EmotionCategoryOrdering
import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.EmotionWithPalette
import app.pbbls.android.features.path.models.Valence

/**
 * One emotion category + the emotions inside it, ready to render in the picker.
 * [palette] is the category palette (all emotions in a category share it), used
 * for the section header accent dot.
 */
data class CategoryGroup(
    val categorySlug: String,
    val categoryName: String,
    val palette: EmotionPalette,
    val rows: List<EmotionWithPalette>,
)

/**
 * Pure grouping + ordering for the emotion picker (D14) — the JVM-testable core
 * of `EmotionPickerSheet` (EmotionPickerGroupingTest). Emotions are grouped by
 * category, empty categories are dropped, and the categories are ordered by
 * [EmotionCategoryOrdering.order] for the selected valence (default MEDIUM ·
 * NEUTRAL when none). Categories absent from the ordering table fall to the end,
 * preserving input order. Intra-category row order is DB-name-stable here; the
 * composable body re-sorts by the localized name (which needs a `@Composable`).
 */
object EmotionPickerGrouping {
    fun groups(
        rows: Collection<EmotionWithPalette>,
        valence: Valence?,
    ): List<CategoryGroup> {
        val byCategory = rows.groupBy { it.categorySlug }
        val ordering = EmotionCategoryOrdering.order(valence)
        val orderedSlugs =
            byCategory.keys.sortedWith(
                compareBy { slug ->
                    val index = ordering.indexOf(slug)
                    if (index >= 0) index else Int.MAX_VALUE
                },
            )
        return orderedSlugs.mapNotNull { slug ->
            val categoryRows = byCategory[slug] ?: return@mapNotNull null
            val first = categoryRows.first()
            CategoryGroup(
                categorySlug = slug,
                categoryName = first.categoryName,
                palette = first.palette,
                rows = categoryRows.sortedBy { it.name },
            )
        }
    }
}

package app.pbbls.android.features.path.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Raw row of `public.v_emotions_with_palette`. Every column is nullable at
 * the PostgREST layer (view types don't propagate NOT NULL), so the row
 * decodes leniently and [toEmotionWithPalette] enforces the real contract —
 * a bad row maps to `null` and the service logs + skips it instead of
 * failing the whole load (slightly more forgiving than iOS, which rejects
 * the entire response on one bad row).
 */
@Serializable
data class EmotionWithPaletteRow(
    val id: String? = null,
    val slug: String? = null,
    val name: String? = null,
    val emoji: String? = null,
    @SerialName("category_id")
    val categoryId: String? = null,
    @SerialName("category_slug")
    val categorySlug: String? = null,
    @SerialName("category_name")
    val categoryName: String? = null,
    @SerialName("primary_color")
    val primaryColor: String? = null,
    @SerialName("secondary_color")
    val secondaryColor: String? = null,
    @SerialName("light_color")
    val lightColor: String? = null,
    @SerialName("surface_color")
    val surfaceColor: String? = null,
) {
    fun toEmotionWithPalette(): EmotionWithPalette? {
        val palette =
            EmotionPalette.fromHex(
                primaryHex = primaryColor ?: return null,
                secondaryHex = secondaryColor ?: return null,
                lightHex = lightColor ?: return null,
                surfaceHex = surfaceColor ?: return null,
            ) ?: return null
        return EmotionWithPalette(
            id = id ?: return null,
            slug = slug ?: return null,
            name = name ?: return null,
            emoji = emoji ?: return null,
            categoryId = categoryId ?: return null,
            categorySlug = categorySlug ?: return null,
            categoryName = categoryName ?: return null,
            palette = palette,
        )
    }
}

/** Validated emotion + category palette — mirrors iOS `EmotionWithPalette`. */
data class EmotionWithPalette(
    val id: String,
    val slug: String,
    val name: String,
    val emoji: String,
    val categoryId: String,
    val categorySlug: String,
    val categoryName: String,
    val palette: EmotionPalette,
)

package app.pbbls.android.core.model

import kotlinx.serialization.Serializable

/**
 * The nested `emotion` object embedded by `path_pebbles()` —
 * `{id, slug, name}`. Palette colors are NOT here; they come from
 * `EmotionPaletteService.palette(emotionId)`. Never render [name] directly —
 * resolve through `ReferenceStrings.referenceName` (slug-keyed, falls back to
 * this DB name).
 */
@Serializable
data class EmotionRef(
    val id: String,
    val slug: String,
    val name: String,
)

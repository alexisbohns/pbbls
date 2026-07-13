package app.pbbls.android.features.profile.models

import app.pbbls.android.features.glyph.models.Glyph
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Clean domain type — a soul + its glyph + live pebble count. Mirrors iOS SoulWithGlyph. */
data class SoulWithGlyph(
    val id: String,
    val name: String,
    val glyphId: String,
    val glyph: Glyph,
    val pebblesCount: Int = 0,
)

/**
 * Wire row for `souls` selects. PostgREST nests the joined glyph under the FK
 * relation name `glyphs` and returns `pebble_souls(count)` as `[{ "count": N }]`;
 * absent (the reference-data select doesn't ask for it) decodes to 0.
 */
@Serializable
data class SoulRow(
    val id: String,
    val name: String,
    @SerialName("glyph_id")
    val glyphId: String,
    @SerialName("glyphs")
    val glyph: Glyph,
    @SerialName("pebbles_count")
    val pebblesCount: List<CountRow> = emptyList(),
) {
    fun toSoulWithGlyph(): SoulWithGlyph =
        SoulWithGlyph(
            id = id,
            name = name,
            glyphId = glyphId,
            glyph = glyph,
            pebblesCount = pebblesCount.firstOrNull()?.count ?: 0,
        )

    @Serializable
    data class CountRow(
        val count: Int,
    )
}

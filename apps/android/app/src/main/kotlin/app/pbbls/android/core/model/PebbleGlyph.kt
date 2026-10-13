package app.pbbls.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A pebble's derived glyph (spec D5): its oldest collection's glyph, else its
 * first domain's default, as resolved server-side by `resolve_pebble_glyph`.
 * Pebbles carry no glyph of their own any more; clients draw these strokes.
 *
 * [source] stays a string (`"collection"` or `"domain"`): an enum would make a
 * new server value a decode failure for the whole timeline.
 */
@Serializable
data class PebbleGlyph(
    val id: String,
    val name: String? = null,
    val strokes: List<GlyphStroke>,
    @SerialName("view_box")
    val viewBox: String,
    val source: String? = null,
)

package app.pbbls.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Full glyph model — mirrors iOS `Glyph.swift`. Stored in `public.glyphs`.
 *
 * [viewBox] is `"0 0 200 200"` for glyphs carved on iOS; imported web glyphs
 * may use other view-box values. [name] and [userId] are nullable and default
 * to `null`: a system glyph has no owner, and column-restricted selects
 * (e.g. the pebble-detail `glyphs(id, name, strokes, view_box)` embed) omit
 * `user_id` entirely — an absent key must still decode.
 *
 * [isSystem] is the first-party marker (#872) and defaults to `false` for the
 * same absent-key reason. It is NOT `userId == null`: `purge_account` keeps a
 * SOLD glyph when its creator deletes their account and anonymizes it, and that
 * row stays entitlement-gated. Filter pickers on this, never on `userId`.
 */
@Serializable
data class Glyph(
    val id: String,
    val name: String? = null,
    val strokes: List<GlyphStroke>,
    @SerialName("view_box")
    val viewBox: String,
    @SerialName("user_id")
    val userId: String? = null,
    @SerialName("is_system")
    val isSystem: Boolean = false,
)

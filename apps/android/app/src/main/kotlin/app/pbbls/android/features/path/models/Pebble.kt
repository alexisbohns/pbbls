package app.pbbls.android.features.path.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

/**
 * One row of the `path_pebbles()` RPC — mirrors iOS `Pebble.swift`.
 *
 * Hand-written per surface by design (decision log 2026-07-10: no shared
 * types / codegen). Nullable columns default to `null` so absent keys decode
 * regardless of the client's Json configuration.
 */
@Serializable
data class Pebble(
    val id: String,
    val name: String,
    @SerialName("happened_at")
    @Serializable(with = OffsetDateTimeSerializer::class)
    val happenedAt: OffsetDateTime,
    @SerialName("created_at")
    @Serializable(with = OffsetDateTimeSerializer::class)
    val createdAt: OffsetDateTime,
    // 1=small, 2=medium, 3=large (DB CHECK 1..3)
    val intensity: Int,
    // -1=lowlight, 0=neutral, +1=highlight (DB CHECK -1..1)
    val positiveness: Int,
    @SerialName("render_svg")
    val renderSvg: String? = null,
    val emotion: EmotionRef? = null,
    // Storage *prefix* `{user_id}/{snap_id}` — append `/thumb.jpg` or
    // `/original.jpg` before signing.
    @SerialName("first_snap_path")
    val firstSnapPath: String? = null,
) {
    /** Derived from `(positiveness, intensity)` — see [Valence.fromOrDefault]. */
    val valence: Valence
        get() = Valence.fromOrDefault(positiveness, intensity)
}

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

package app.pbbls.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

/**
 * One row of `path_moments()`: the caller's moments, newest `happened_at`
 * first. A null [pebble] is an uncompleted moment, drawn as a blank stone.
 * [name] is null on a photo-only moment (spec D3: a name or a photo).
 */
@Serializable
data class PathMoment(
    val id: String,
    val name: String? = null,
    @SerialName("happened_at")
    @Serializable(with = WholeSecondTimestampSerializer::class)
    val happenedAt: OffsetDateTime,
    @SerialName("created_at")
    @Serializable(with = WholeSecondTimestampSerializer::class)
    val createdAt: OffsetDateTime,
    /** Storage prefix `{user_id}/{snap_id}`; append `/thumb.jpg` or `/original.jpg` before signing. */
    @SerialName("first_snap_path")
    val firstSnapPath: String? = null,
    val pebble: PathPebble? = null,
) {
    val isCompleted: Boolean get() = pebble != null

    /** The stone's shape. An uncompleted moment uses the neutral medium stone (R4). */
    val valence: Valence get() = pebble?.valence ?: Valence.NEUTRAL_MEDIUM
}

/** The caller's pebble on a Path row. Palette colours come from `EmotionPaletteService` by [emotion] id. */
@Serializable
data class PathPebble(
    val id: String,
    val intensity: Int,
    val positiveness: Int,
    val visibility: Visibility = Visibility.SECRET,
    val emotion: EmotionRef? = null,
    val glyph: PebbleGlyph? = null,
) {
    val valence: Valence get() = Valence.fromOrDefault(positiveness, intensity)
}

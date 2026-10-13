package app.pbbls.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

/**
 * One row of `v_moments_full`: the moment, its snaps, and the CALLER's pebble
 * on it (null when uncompleted). Used by the read page, the edit form and the
 * soul and collection lists.
 */
@Serializable
data class MomentDetail(
    val id: String,
    val name: String? = null,
    val description: String? = null,
    @SerialName("happened_at")
    @Serializable(with = WholeSecondTimestampSerializer::class)
    val happenedAt: OffsetDateTime,
    @SerialName("created_at")
    @Serializable(with = WholeSecondTimestampSerializer::class)
    val createdAt: OffsetDateTime,
    val snaps: List<MomentSnap> = emptyList(),
    val pebble: MomentPebble? = null,
) {
    val sortedSnaps: List<MomentSnap> get() = snaps.sortedBy { it.sortOrder }

    fun toPathMoment(): PathMoment =
        PathMoment(
            id = id,
            name = name,
            happenedAt = happenedAt,
            createdAt = createdAt,
            firstSnapPath = sortedSnaps.firstOrNull()?.storagePath,
            pebble =
                pebble?.let {
                    PathPebble(
                        id = it.id,
                        intensity = it.intensity,
                        positiveness = it.positiveness,
                        visibility = it.visibility,
                        emotion = it.emotion,
                        glyph = it.glyph,
                    )
                },
        )
}

@Serializable
data class MomentSnap(
    val id: String,
    @SerialName("storage_path")
    val storagePath: String,
    @SerialName("sort_order")
    val sortOrder: Int = 0,
)

@Serializable
data class MomentPebble(
    val id: String,
    val intensity: Int,
    val positiveness: Int,
    val visibility: Visibility,
    val emotion: EmotionRef,
    val glyph: PebbleGlyph? = null,
    val souls: List<SoulRef> = emptyList(),
    val domains: List<DomainRef> = emptyList(),
    val collections: List<PebbleCollection> = emptyList(),
) {
    val valence: Valence get() = Valence.fromOrDefault(positiveness, intensity)
}

/** The view projects souls as `{id, name}`; glyphs come from the souls cache (A-15). */
@Serializable
data class SoulRef(
    val id: String,
    val name: String,
)

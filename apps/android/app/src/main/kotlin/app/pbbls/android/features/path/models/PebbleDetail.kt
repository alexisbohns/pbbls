package app.pbbls.android.features.path.models

import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.features.profile.models.SoulRow
import app.pbbls.android.features.profile.models.SoulWithGlyph
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

/** 3-field embedded domain ref — the detail select omits `label`. Mirrors iOS DomainRef. */
@Serializable
data class DomainRef(
    val id: String,
    val slug: String,
    val name: String,
)

/**
 * Read model for the pebble detail sheet (D7) — mirrors iOS `PebbleDetail.swift`.
 * Decodes one pebble row with embedded relations from the direct PostgREST
 * select. Junction-table rows decode into the private nested wrapper types and
 * are flattened by the [domains] / [souls] / [collections] accessors, so callers
 * see clean arrays. [sortedSnaps] round-trips into the update payload (risk 5);
 * `valence` derives via [Valence.fromOrDefault].
 */
@Serializable
data class PebbleDetail(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("happened_at")
    @Serializable(with = OffsetDateTimeSerializer::class)
    val happenedAt: OffsetDateTime,
    val intensity: Int,
    val positiveness: Int,
    val visibility: Visibility,
    @SerialName("render_svg")
    val renderSvg: String? = null,
    @SerialName("render_version")
    val renderVersion: String? = null,
    @SerialName("glyph_id")
    val glyphId: String? = null,
    val glyph: Glyph? = null,
    val emotion: EmotionRef,
    @SerialName("pebble_domains")
    private val pebbleDomains: List<DomainWrapper> = emptyList(),
    @SerialName("pebble_souls")
    private val pebbleSouls: List<SoulWrapper> = emptyList(),
    @SerialName("collection_pebbles")
    private val collectionPebbles: List<CollectionWrapper> = emptyList(),
    private val snaps: List<SnapRef> = emptyList(),
) {
    val domains: List<DomainRef> get() = pebbleDomains.map { it.domain }
    val souls: List<SoulWithGlyph> get() = pebbleSouls.map { it.soul.toSoulWithGlyph() }
    val collections: List<PebbleCollection> get() = collectionPebbles.map { it.collection }
    val sortedSnaps: List<SnapRef> get() = snaps.sortedBy { it.sortOrder }
    val valence: Valence get() = Valence.fromOrDefault(positiveness, intensity)

    @Serializable
    data class DomainWrapper(
        val domain: DomainRef,
    )

    @Serializable
    data class SoulWrapper(
        val soul: SoulRow,
    )

    @Serializable
    data class CollectionWrapper(
        val collection: PebbleCollection,
    )

    @Serializable
    data class SnapRef(
        val id: String,
        @SerialName("storage_path")
        val storagePath: String,
        @SerialName("sort_order")
        val sortOrder: Int,
    )
}

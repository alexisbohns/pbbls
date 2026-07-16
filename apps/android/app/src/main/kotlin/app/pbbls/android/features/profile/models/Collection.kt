package app.pbbls.android.features.profile.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mode variants for a collection — mirrors the `mode` check constraint on
 * `public.collections`: `('stack', 'pack', 'track')` or null.
 */
@Serializable
enum class CollectionMode {
    @SerialName("stack")
    STACK,

    @SerialName("pack")
    PACK,

    @SerialName("track")
    TRACK,
}

/**
 * Clean domain type for a collection with its live pebble count — ports iOS
 * `Collection.swift` (D10). Distinct from the form's bare
 * [app.pbbls.android.features.path.models.PebbleCollection] `{id, name}`
 * reference model; list/detail/profile surfaces use this richer shape.
 */
data class Collection(
    val id: String,
    val name: String,
    val mode: CollectionMode?,
    val pebbleCount: Int,
)

/**
 * Wire row for `collections` selects with the
 * `pebble_count:collection_pebbles(count)` nested aggregate, which PostgREST
 * returns as `[{ "count": N }]`; an absent aggregate (e.g. a single-row detail
 * fetch) decodes to 0 defensively. Note the junction is `collection_pebbles`
 * — the opposite word order from `pebble_souls`.
 */
@Serializable
data class CollectionRow(
    val id: String,
    val name: String,
    val mode: CollectionMode? = null,
    @SerialName("pebble_count")
    val pebbleCount: List<CountRow> = emptyList(),
) {
    fun toCollection(): Collection =
        Collection(
            id = id,
            name = name,
            mode = mode,
            pebbleCount = pebbleCount.firstOrNull()?.count ?: 0,
        )

    @Serializable
    data class CountRow(
        val count: Int,
    )
}

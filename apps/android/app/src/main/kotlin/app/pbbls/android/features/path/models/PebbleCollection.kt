package app.pbbls.android.features.path.models

import kotlinx.serialization.Serializable

/**
 * Reference-data collection — mirrors iOS `PebbleCollection.swift`. Loaded by
 * the reference data service for the form's collection picker and embedded in
 * [PebbleDetail] via the `collection_pebbles` junction.
 */
@Serializable
data class PebbleCollection(
    val id: String,
    val name: String,
)

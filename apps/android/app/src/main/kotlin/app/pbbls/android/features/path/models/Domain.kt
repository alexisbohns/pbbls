package app.pbbls.android.features.path.models

import kotlinx.serialization.Serializable

/**
 * Reference-data domain — mirrors iOS `Domain.swift`. Loaded by the reference
 * data service for the create/edit form's domain picker. Never render [name]
 * directly — resolve through `ReferenceStrings.referenceName` (slug-keyed,
 * falls back to this DB name).
 */
@Serializable
data class Domain(
    val id: String,
    val slug: String,
    val name: String,
    val label: String,
)

package app.pbbls.android.features.path.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Pebble visibility — mirrors iOS `Visibility.swift`. `@SerialName` on each
 * entry decodes/encodes the DB strings `"private"` / `"public"`, so a payload's
 * `visibility: Visibility` field serializes straight to the wire string.
 */
@Serializable
enum class Visibility {
    @SerialName("private")
    PRIVATE,

    @SerialName("public")
    PUBLIC,
}

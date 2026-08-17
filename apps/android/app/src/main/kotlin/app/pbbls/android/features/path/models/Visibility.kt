package app.pbbls.android.features.path.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Pebble privacy grade (M51) — mirrors iOS `Visibility.swift`. `@SerialName`
 * on each entry decodes/encodes the DB strings, so a payload's
 * `visibility: Visibility` field serializes straight to the wire string.
 *
 * `SECRET` = owner-only (the default since the M51 backfill), `PRIVATE` =
 * owner + mutual connections, `PUBLIC` = anyone with the /p link.
 */
@Serializable
enum class Visibility {
    @SerialName("secret")
    SECRET,

    @SerialName("private")
    PRIVATE,

    @SerialName("public")
    PUBLIC,
}

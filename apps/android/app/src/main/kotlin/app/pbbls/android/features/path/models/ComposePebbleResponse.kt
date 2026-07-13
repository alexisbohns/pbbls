package app.pbbls.android.features.path.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Decoded body of the `compose-pebble` / `compose-pebble-update` edge-function
 * response — mirrors iOS `ComposePebbleResponse.swift`.
 *
 * All fields except [pebbleId] are nullable because a soft-success 5xx body
 * carries only `pebble_id` when the insert succeeded but the compose step
 * failed; the client still advances to the detail reveal and renders text-only
 * when [renderSvg] is null.
 */
@Serializable
data class ComposePebbleResponse(
    @SerialName("pebble_id")
    val pebbleId: String,
    @SerialName("render_svg")
    val renderSvg: String? = null,
    @SerialName("render_version")
    val renderVersion: String? = null,
    @SerialName("karma_delta")
    val karmaDelta: Int? = null,
)

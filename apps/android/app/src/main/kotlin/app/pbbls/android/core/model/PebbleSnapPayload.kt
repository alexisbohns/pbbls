package app.pbbls.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One snap link in an update payload — `{id, storage_path, sort_order}`.
 * Mirrors the nested `SnapPayload` in iOS `PebbleUpdatePayload.swift`.
 */
@Serializable
data class PebbleSnapPayload(
    val id: String,
    @SerialName("storage_path")
    val storagePath: String,
    @SerialName("sort_order")
    val sortOrder: Int,
)

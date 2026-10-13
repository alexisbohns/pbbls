package app.pbbls.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One snap link in a write payload — `{id, storage_path, sort_order}`, which is
 * exactly `save_moment`'s `snaps[]` element. Mirrors the nested `SnapPayload`
 * in iOS `PebbleUpdatePayload.swift`.
 */
@Serializable
data class PebbleSnapPayload(
    val id: String,
    @SerialName("storage_path")
    val storagePath: String,
    @SerialName("sort_order")
    val sortOrder: Int,
)

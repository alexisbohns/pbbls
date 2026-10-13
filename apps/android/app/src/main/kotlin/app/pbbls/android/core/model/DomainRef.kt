package app.pbbls.android.core.model

import kotlinx.serialization.Serializable

/** 3-field embedded domain ref — the detail select omits `label`. Mirrors iOS DomainRef. */
@Serializable
data class DomainRef(
    val id: String,
    val slug: String,
    val name: String,
)

package app.pbbls.android.features.profile.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the `v_karma_summary` view (one row per user, filtered by RLS) —
 * ports iOS `KarmaSummary.swift`. [totalKarma] is the sum of
 * `karma_events.delta` for the current user; [pebblesCount] is the user's
 * total pebble count.
 */
@Serializable
data class KarmaSummary(
    @SerialName("total_karma")
    val totalKarma: Int,
    @SerialName("pebbles_count")
    val pebblesCount: Int,
)

package app.pbbls.android.features.profile.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One-row result of `public.get_profile_engagement(p_tz text)` — ports iOS
 * `ProfileEngagement.swift`.
 *
 * [daysPracticed] is the all-time distinct count of calendar days (in the
 * caller's timezone) on which the user created any pebble.
 *
 * [assiduity] is a 28-element bool array: index 0 = 27 days ago, index 27 =
 * today, both bucketed in the caller's timezone. (Postgres serializes
 * 1-indexed; JSON re-indexes to 0.)
 */
@Serializable
data class ProfileEngagement(
    @SerialName("days_practiced")
    val daysPracticed: Int,
    val assiduity: List<Boolean>,
)

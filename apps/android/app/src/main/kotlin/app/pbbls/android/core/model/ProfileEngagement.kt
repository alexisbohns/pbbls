package app.pbbls.android.core.model

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
 *
 * [glyphsCarved] counts the caller's own glyphs and [glyphsOwned] their
 * entitlements to other people's (#1386, spec D17). Both are optional so a
 * server from before #1386 still decodes; null means "not served", and the
 * You lines then read without a number.
 */
@Serializable
data class ProfileEngagement(
    @SerialName("days_practiced")
    val daysPracticed: Int,
    val assiduity: List<Boolean>,
    @SerialName("glyphs_carved")
    val glyphsCarved: Int? = null,
    @SerialName("glyphs_owned")
    val glyphsOwned: Int? = null,
)

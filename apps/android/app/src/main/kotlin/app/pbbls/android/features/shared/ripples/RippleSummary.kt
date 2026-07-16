package app.pbbls.android.features.shared.ripples

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the `public.v_ripple` view — ports iOS `RippleSummary.swift`.
 * [rippleLevel] is a 0–6 integer bucketed from pebbles-in-last-28-days
 * (counted by `created_at`). [activeToday] is true iff the user created at
 * least one pebble today (server-side UTC `current_date` — PathScreen
 * overrides it with a device-local check, the iOS `rippleWithLocalActiveToday`
 * parity workaround).
 */
@Serializable
data class RippleSummary(
    @SerialName("ripple_level")
    val rippleLevel: Int,
    @SerialName("pebbles_28d")
    val pebbles28d: Int,
    @SerialName("active_today")
    val activeToday: Boolean,
) {
    /** `null` once the user has reached level 6 (terminal). */
    val nextLevel: Int?
        get() = if (rippleLevel >= 6) null else rippleLevel + 1

    /**
     * Pebbles still needed in the last-28-days window to reach [nextLevel];
     * `null` once the user has reached level 6.
     */
    val pebblesToNextLevel: Int?
        get() {
            val next = nextLevel ?: return null
            val threshold = LEVEL_ENTRY_THRESHOLDS[next - 1]
            return (threshold - pebbles28d).coerceAtLeast(0)
        }

    companion object {
        /**
         * Minimum `pebbles28d` required to enter levels 1…6. Source of truth:
         * `packages/supabase/supabase/migrations/20260516000001_v_ripple_security_filter.sql`.
         * If those thresholds change, update both places (and iOS).
         */
        private val LEVEL_ENTRY_THRESHOLDS = listOf(1, 5, 9, 13, 17, 21)
    }
}

import Foundation

/// Mirrors the `public.v_ripple` view. `ripple_level` is a 0–6 integer
/// bucketed from pebbles-in-last-28-days (counted by `created_at`).
/// `active_today` is true iff the user created at least one pebble
/// today (server-side `current_date`).
struct RippleSummary: Decodable {
    let rippleLevel: Int
    let pebbles28d: Int
    let activeToday: Bool

    enum CodingKeys: String, CodingKey {
        case rippleLevel = "ripple_level"
        case pebbles28d  = "pebbles_28d"
        case activeToday = "active_today"
    }
}

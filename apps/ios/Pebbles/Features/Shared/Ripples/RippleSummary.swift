import Foundation

/// Mirrors the `public.v_ripple` view. `ripple_level` is a 0–6 integer
/// bucketed from pebbles-in-last-28-days (counted by `created_at`).
/// `active_today` is true iff the user created at least one pebble
/// today (server-side `current_date`).
struct RippleSummary: Decodable, Equatable {
    let rippleLevel: Int
    let pebbles28d: Int
    let activeToday: Bool

    enum CodingKeys: String, CodingKey {
        case rippleLevel = "ripple_level"
        case pebbles28d  = "pebbles_28d"
        case activeToday = "active_today"
    }

    init(rippleLevel: Int, pebbles28d: Int, activeToday: Bool) {
        self.rippleLevel = rippleLevel
        self.pebbles28d = pebbles28d
        self.activeToday = activeToday
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.rippleLevel = try c.decode(Int.self, forKey: .rippleLevel)
        self.pebbles28d  = try c.decode(Int.self, forKey: .pebbles28d)
        self.activeToday = try c.decode(Bool.self, forKey: .activeToday)
    }

    /// Minimum `pebbles28d` required to enter levels 1…6.
    /// Source of truth: `packages/supabase/supabase/migrations/20260516000001_v_ripple_security_filter.sql`.
    /// If those thresholds change, update both places.
    private static let levelEntryThresholds: [Int] = [1, 5, 9, 13, 17, 21]

    /// `nil` once the user has reached level 6 (terminal).
    var nextLevel: Int? {
        rippleLevel >= 6 ? nil : rippleLevel + 1
    }

    /// Pebbles still needed in the last-28-days window to reach `nextLevel`.
    /// `nil` once the user has reached level 6.
    var pebblesToNextLevel: Int? {
        guard let next = nextLevel else { return nil }
        let threshold = Self.levelEntryThresholds[next - 1]
        return max(threshold - pebbles28d, 0)
    }
}

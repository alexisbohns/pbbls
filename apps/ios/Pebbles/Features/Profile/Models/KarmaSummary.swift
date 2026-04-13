import Foundation

/// Mirrors the `v_karma_summary` view (one row per user, filtered by RLS).
/// `total_karma` is the sum of `karma_events.delta` for the current user;
/// `pebbles_count` is the user's total pebble count.
struct KarmaSummary: Decodable {
    let totalKarma: Int
    let pebblesCount: Int

    enum CodingKeys: String, CodingKey {
        case totalKarma = "total_karma"
        case pebblesCount = "pebbles_count"
    }
}

import Foundation

/// Mirrors the `v_bounce` view. `bounce_level` is a 0–7 integer computed
/// from distinct active days over the last 28 days; `active_days` is the
/// raw count used to derive that level.
struct BounceSummary: Decodable {
    let bounceLevel: Int
    let activeDays: Int

    enum CodingKeys: String, CodingKey {
        case bounceLevel = "bounce_level"
        case activeDays = "active_days"
    }
}

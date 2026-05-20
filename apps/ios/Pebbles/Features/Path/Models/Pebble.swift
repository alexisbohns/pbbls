import Foundation
import os

struct Pebble: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
    let happenedAt: Date
    let createdAt: Date
    let intensity: Int                  // 1=small, 2=medium, 3=large
    let positiveness: Int               // -1=lowlight, 0=neutral, +1=highlight
    let renderSvg: String?
    let emotion: EmotionRef?
    let firstSnapPath: String?

    private enum CodingKeys: String, CodingKey {
        case id, name, intensity, positiveness, emotion
        case happenedAt = "happened_at"
        case createdAt = "created_at"
        case renderSvg = "render_svg"
        case firstSnapPath = "first_snap_path"
    }

    /// Derived from `(positiveness, intensity)`. Mirrors
    /// `PebbleDetail.valence`; logs and falls back to `.neutralMedium`
    /// if the pair is out of range (DB CHECKs guarantee the pair, so
    /// this branch is only reachable via decode drift).
    var valence: Valence {
        switch (positiveness, intensity) {
        case (-1, 1): return .lowlightSmall
        case (-1, 2): return .lowlightMedium
        case (-1, 3): return .lowlightLarge
        case ( 0, 1): return .neutralSmall
        case ( 0, 2): return .neutralMedium
        case ( 0, 3): return .neutralLarge
        case ( 1, 1): return .highlightSmall
        case ( 1, 2): return .highlightMedium
        case ( 1, 3): return .highlightLarge
        default:
            Logger(subsystem: "app.pbbls.ios", category: "pebble-model").error(
                """
                unexpected (positiveness, intensity) pair: \
                (\(self.positiveness, privacy: .public), \(self.intensity, privacy: .public)) \
                — defaulting to neutralMedium
                """
            )
            return .neutralMedium
        }
    }
}

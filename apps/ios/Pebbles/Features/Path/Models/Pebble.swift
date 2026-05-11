import Foundation

struct Pebble: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
    let happenedAt: Date
    let intensity: Int                  // 1=small, 2=medium, 3=large
    let renderSvg: String?
    let emotion: EmotionRef?
    let firstSnapPath: String?

    private enum CodingKeys: String, CodingKey {
        case id, name, intensity, emotion
        case happenedAt = "happened_at"
        case renderSvg = "render_svg"
        case firstSnapPath = "first_snap_path"
    }
}

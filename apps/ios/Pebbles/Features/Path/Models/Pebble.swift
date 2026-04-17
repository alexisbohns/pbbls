import Foundation

struct Pebble: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
    let happenedAt: Date
    let renderSvg: String?
    let emotion: EmotionRef?

    private enum CodingKeys: String, CodingKey {
        case id
        case name
        case happenedAt = "happened_at"
        case renderSvg = "render_svg"
        case emotion
    }
}

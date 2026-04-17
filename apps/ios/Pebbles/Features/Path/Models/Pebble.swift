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

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try container.decode(UUID.self, forKey: .id)
        self.name = try container.decode(String.self, forKey: .name)
        self.happenedAt = try container.decode(Date.self, forKey: .happenedAt)
        self.renderSvg = try container.decodeIfPresent(String.self, forKey: .renderSvg)
        self.emotion = try container.decodeIfPresent(EmotionRef.self, forKey: .emotion)
    }
}

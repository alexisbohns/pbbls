import Foundation

struct Soul: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
    let glyphId: UUID

    enum CodingKeys: String, CodingKey {
        case id
        case name
        case glyphId = "glyph_id"
    }
}

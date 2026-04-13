import Foundation

struct Pebble: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
    let happenedAt: Date

    enum CodingKeys: String, CodingKey {
        case id
        case name
        case happenedAt = "happened_at"
    }
}

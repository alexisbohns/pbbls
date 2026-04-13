import Foundation

struct Emotion: Identifiable, Decodable, Hashable {
    let id: UUID
    let slug: String
    let name: String
    let color: String
}

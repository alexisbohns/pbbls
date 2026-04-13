import Foundation

struct Domain: Identifiable, Decodable, Hashable {
    let id: UUID
    let slug: String
    let name: String
    let label: String
}

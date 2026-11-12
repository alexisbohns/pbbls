import Foundation

/// The projection both report RPCs return: `{id, status, created_at}`.
/// `created_at` is parsed tolerantly (standing timestamp rule), never left to
/// the ambient decoder's date strategy.
struct ReportReceipt: Decodable, Equatable, Sendable {
    let id: UUID
    let status: String
    let createdAt: Date

    enum CodingKeys: String, CodingKey {
        case id
        case status
        case createdAt = "created_at"
    }

    init(id: UUID, status: String, createdAt: Date) {
        self.id = id
        self.status = status
        self.createdAt = createdAt
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(UUID.self, forKey: .id)
        status = try container.decode(String.self, forKey: .status)
        let raw = try container.decode(String.self, forKey: .createdAt)
        guard let date = ISO8601Flexible.parse(raw) else {
            throw DecodingError.dataCorruptedError(
                forKey: .createdAt, in: container, debugDescription: "not ISO-8601: \(raw)")
        }
        createdAt = date
    }
}

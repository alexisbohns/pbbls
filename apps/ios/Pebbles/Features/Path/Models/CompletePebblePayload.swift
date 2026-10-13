import Foundation

/// The `payload` of `complete_pebble(payload jsonb)` (spec §3): the caller's
/// one pebble on `momentId`, inserted or updated. Keys other than
/// `moment_id` are omitted when nil (null means "keep"). iOS never sends
/// `new_souls`, `new_collections` or `cards` (A2).
struct CompletePebblePayload: Encodable, Equatable {
    let momentId: UUID
    var emotionId: UUID?
    var intensity: Int?
    var positiveness: Int?
    var visibility: Visibility?
    var domainIds: [UUID]?
    var soulIds: [UUID]?
    var collectionIds: [UUID]?

    init(momentId: UUID) {
        self.momentId = momentId
    }

    private enum CodingKeys: String, CodingKey {
        case intensity, positiveness, visibility
        case momentId = "moment_id"
        case emotionId = "emotion_id"
        case domainIds = "domain_ids"
        case soulIds = "soul_ids"
        case collectionIds = "collection_ids"
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        func lower(_ ids: [UUID]?) -> [String]? { ids?.map { $0.uuidString.lowercased() } }
        try container.encode(momentId.uuidString.lowercased(), forKey: .momentId)
        try container.encodeIfPresent(emotionId.map { $0.uuidString.lowercased() }, forKey: .emotionId)
        try container.encodeIfPresent(intensity, forKey: .intensity)
        try container.encodeIfPresent(positiveness, forKey: .positiveness)
        try container.encodeIfPresent(visibility?.rawValue, forKey: .visibility)
        try container.encodeIfPresent(lower(domainIds), forKey: .domainIds)
        try container.encodeIfPresent(lower(soulIds), forKey: .soulIds)
        try container.encodeIfPresent(lower(collectionIds), forKey: .collectionIds)
    }
}

extension CompletePebblePayload {
    /// A full completion from the flow or Edit pebble (A2). Nil until the
    /// draft is complete, so a caller cannot send a half pebble.
    init?(momentId: UUID, draft: CompleteDraft) {
        guard draft.isComplete, let valence = draft.valence,
              let emotionId = draft.emotionId, let domainId = draft.domainId else { return nil }
        self.init(momentId: momentId)
        self.emotionId = emotionId
        self.intensity = valence.intensity
        self.positiveness = valence.positiveness
        self.visibility = draft.visibility
        self.domainIds = [domainId]
        self.soulIds = draft.soulIds
        self.collectionIds = draft.collectionId.map { [$0] } ?? []
    }
}

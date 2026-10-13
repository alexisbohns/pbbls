import Foundation

/// The Complete flow's answers (spec §6). Complete = valence + emotion +
/// domain (D2); souls and collection are optional; visibility defaults to
/// secret. One domain and one collection at most (A1).
struct CompleteDraft: Equatable {
    var valence: Valence?
    var emotionId: UUID?
    var domainId: UUID?
    var soulIds: [UUID] = []
    var collectionId: UUID?
    var visibility: Visibility = .secret

    var isComplete: Bool { valence != nil && emotionId != nil && domainId != nil }

    /// Nothing the user picked. Visibility is defaulted, so it never counts.
    var isEmpty: Bool {
        valence == nil && emotionId == nil && domainId == nil && soulIds.isEmpty && collectionId == nil
    }

    struct KnownIds {
        let soulIds: Set<UUID>
        let collectionIds: Set<UUID>
    }

    /// Souls and collections are deletable; a restored draft can outlive
    /// them. Dropping them here costs a chip, not a `soul_not_owned` later.
    func sanitized(known: KnownIds) -> CompleteDraft {
        var copy = self
        copy.soulIds = soulIds.filter { known.soulIds.contains($0) }
        if let collectionId, !known.collectionIds.contains(collectionId) { copy.collectionId = nil }
        return copy
    }
}

extension CompleteDraft {
    /// Prefill Edit pebble from the caller's pebble (R5). Keeps the first
    /// domain and the first collection, as the pre-split edit did (A6).
    init(from pebble: MomentDetail.Pebble) {
        self.init()
        valence = pebble.valence
        emotionId = pebble.emotion.id
        domainId = pebble.domains.first?.id
        soulIds = pebble.souls.map(\.id)
        collectionId = pebble.collections.first?.id
        visibility = pebble.visibility
    }
}

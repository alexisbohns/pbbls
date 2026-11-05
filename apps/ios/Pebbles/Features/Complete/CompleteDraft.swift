import Foundation

/// The Complete flow's answers (spec §6). Complete = valence + emotion + at
/// least one domain (D2, D10); souls and collection are optional; visibility
/// defaults to secret. Three domains and one collection at most.
struct CompleteDraft: Equatable {
    /// How many domains a pebble carries at most (D10).
    static let domainCap = 3

    /// What Edit pebble prefilled (#1099 D8): the pebble's domains and its
    /// first collection. `CompletePebblePayload` omits a set the user left as
    /// prefilled, so `complete_pebble` keeps the pebble's whole set.
    struct Prefill: Equatable {
        let domainIds: [UUID]
        let collectionId: UUID?
    }

    var valence: Valence?
    var emotionId: UUID?
    /// In tap order, `domainCap` at most.
    var domainIds: [UUID] = []
    var soulIds: [UUID] = []
    var collectionId: UUID?
    var visibility: Visibility = .secret
    /// Set only by `init(from:)`; nil in the Complete flow, so a first
    /// completion sends both sets. Never counts toward `isEmpty`. Not carried
    /// by `CompleteDraftSnapshot`: Edit pebble never persists its draft, and an
    /// autosave that did would turn an edit into a set-collapsing one.
    var prefill: Prefill?

    var isComplete: Bool { valence != nil && emotionId != nil && !domainIds.isEmpty }

    /// Nothing the user picked. Visibility is defaulted, so it never counts.
    var isEmpty: Bool {
        valence == nil && emotionId == nil && domainIds.isEmpty && soulIds.isEmpty && collectionId == nil
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
    /// Prefill Edit pebble from the caller's pebble (R5). Keeps the domains
    /// up to the cap and the first collection, as the pre-split edit did (A6).
    init(from pebble: MomentDetail.Pebble) {
        self.init()
        valence = pebble.valence
        emotionId = pebble.emotion.id
        domainIds = Array(pebble.domains.map(\.id).prefix(Self.domainCap))
        soulIds = pebble.souls.map(\.id)
        collectionId = pebble.collections.first?.id
        visibility = pebble.visibility
        prefill = Prefill(domainIds: domainIds, collectionId: collectionId)
    }
}

import Foundation

extension MomentDetail {
    /// The detail the held card draws before `v_moments_full` answers (#1160):
    /// the inverse of `MomentSummary(detail:)`, so the card is one card from
    /// its first frame and the load fills it in place. The seed has no words,
    /// domains or collections, and no owner; `hiddenAt` is nil, the dates are
    /// the seed's. Nil when the seed's pebble names no emotion: a face cannot
    /// be drawn without one.
    init?(seed: MomentSummary) {
        var pebble: Pebble?
        if let summary = seed.pebble {
            guard let emotion = summary.emotion else { return nil }
            pebble = Pebble(id: summary.id, intensity: summary.intensity, positiveness: summary.positiveness,
                            visibility: summary.visibility, emotion: emotion, glyph: summary.glyph, domains: [],
                            souls: summary.souls.map { SoulRef(id: $0.id, name: $0.name) }, collections: [])
        }
        id = seed.id
        userId = Self.unknownUser
        name = seed.name
        description = nil
        happenedAt = seed.happenedAt
        hiddenAt = nil
        createdAt = seed.createdAt
        updatedAt = seed.createdAt
        snaps = seed.firstSnapPath.map { [Snap(id: seed.id, storagePath: $0, sortOrder: 0)] } ?? []
        self.pebble = pebble
    }

    /// A seed names no owner; the page never reads it before the load.
    private static let unknownUser = UUID(uuid: (0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
}

extension MomentDetail.Pebble {
    init(id: UUID, intensity: Int, positiveness: Int, visibility: Visibility, emotion: EmotionRef, glyph: PebbleGlyph?,
         domains: [DomainRef], souls: [MomentDetail.SoulRef], collections: [MomentDetail.CollectionRef]) {
        self.id = id
        self.intensity = intensity
        self.positiveness = positiveness
        self.visibility = visibility
        self.emotion = emotion
        self.glyph = glyph
        self.domains = domains
        self.souls = souls
        self.collections = collections
    }
}

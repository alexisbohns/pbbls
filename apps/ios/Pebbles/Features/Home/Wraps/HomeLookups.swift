import Foundation

/// What Home's wraps resolve their ids against: the reference data and the
/// palettes, as the Path builds them, minus the moments (Home holds no feed,
/// so `moment` stays nil and a milestone has no card to open here).
enum HomeLookups {
    @MainActor static func make(palettes: EmotionPaletteService, refs: ReferenceDataService) -> WrapLookups {
        let families = Dictionary(EmotionFamilies.of(palettes.byEmotionId.values).map { ($0.slug, $0) },
                                  uniquingKeysWith: { first, _ in first })
        let domains = Dictionary(refs.domains.map { ($0.id.uuidString.lowercased(), $0) },
                                 uniquingKeysWith: { first, _ in first })
        let emotions = palettes.byEmotionId
        let souls = Dictionary(refs.souls.map { ($0.id.uuidString.lowercased(), $0) },
                               uniquingKeysWith: { first, _ in first })
        return WrapLookups(
            moment: { _ in nil },
            family: { families[$0] },
            domain: { domains[$0.lowercased()] },
            emotion: { UUID(uuidString: $0).flatMap { emotions[$0] } },
            palette: { moment in moment.pebble?.emotion.flatMap { palettes.palette(for: $0.id) } },
            soulName: { souls[$0.lowercased()]?.name },
            soulGlyph: { souls[$0.lowercased()]?.glyph.strokes }
        )
    }
}

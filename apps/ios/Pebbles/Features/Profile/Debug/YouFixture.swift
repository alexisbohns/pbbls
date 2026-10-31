#if DEBUG
import Foundation

/// The references' You (#1382): Alexis, a member since March 2026, with a
/// glyph, Ripples level 3 active today, 112 days, 318 pebbles, 1,240 karma.
@MainActor
enum YouFixture {
    static let profile = YouProfile(
        displayName: "Alexis",
        createdAt: ISO8601DateFormatter().date(from: "2026-03-14T10:00:00Z") ?? .distantPast,
        glyphId: UUID(uuidString: "00000000-0000-0000-0002-000000000001"),
        handle: "alexis", publicProfile: true)

    /// A small carved mark: a stone and a wave, in the 200 × 200 glyph box.
    static let strokes: [GlyphStroke] = [
        GlyphStroke(d: "M60,120 C60,80 140,80 140,120 C140,150 60,150 60,120 Z", width: 6),
        GlyphStroke(d: "M40,60 C70,40 90,80 120,60 C140,48 150,52 165,60", width: 6)
    ]

    static let stats = YouStats(rippleLevel: 3, activeToday: true, daysPracticed: 112, pebbles: 318, karma: 1240)

    /// A loaded model; `glyph: false` draws the header's empty mark.
    static func model(glyph: Bool = true) async -> YouModel {
        let model = YouModel()
        var row = profile
        if !glyph { row.glyphId = nil }
        model.service = YouFixtureService(profile: row, strokes: strokes)
        await model.loadProfile()
        return model
    }
}

/// Fixed answers; nothing reaches the server.
struct YouFixtureService: YouServicing {
    let profile: YouProfile
    let strokes: [GlyphStroke]

    func profile() async throws -> YouProfile { profile }
    func glyphStrokes(id: UUID) async throws -> [GlyphStroke] { strokes }
}
#endif

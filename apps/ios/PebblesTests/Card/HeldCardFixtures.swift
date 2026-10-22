import Foundation
import SwiftUI
@testable import Pebbles

/// The held card's fixtures (#1146 §Testing): F1 a photo card, F2 a public one,
/// F3 a card without a photo, F4 a bare moment (E3), and a stress verso with
/// four long domains and two long collections. Photos are the Spine's fixture
/// photos, drawn through `SpineFixturePhotos.source`.
enum HeldCardFixtures {
    static let joy = EmotionPalette(primaryHex: "#E8A23AFF", secondaryHex: "#F2C46DFF", lightHex: "#FBE7C2FF",
                                    surfaceHex: "#E8A23A1A", darkHex: "#5C3A0EFF", shadedHex: "#A4691FFF")
    static let blue = EmotionPalette(primaryHex: "#4A78B8FF", secondaryHex: "#86A7D6FF", lightHex: "#DCE7F5FF",
                                     surfaceHex: "#4A78B81A", darkHex: "#1B2F4DFF", shadedHex: "#33547FFF")

    static let fountain: String = SpineFixtureWeek.photos.first { $0.value.file == "fountain" }?.key ?? "missing"
    static let seawall: String = SpineFixtureWeek.photos.first { $0.value.file == "seawall-portrait" }?.key ?? "missing"
    static func ratio(_ path: String) -> CGFloat? { SpineFixtureWeek.photos[path]?.ratio }

    static let work = UUID(uuidString: "33333333-3333-4333-8333-333333333301") ?? UUID()
    static let friends = UUID(uuidString: "33333333-3333-4333-8333-333333333302") ?? UUID()
    static let growth = UUID(uuidString: "33333333-3333-4333-8333-333333333303") ?? UUID()
    static let spirit = UUID(uuidString: "33333333-3333-4333-8333-333333333304") ?? UUID()
    static let autumn = UUID(uuidString: "55555555-5555-4555-8555-555555555501") ?? UUID()

    static let domains: [Domain] = [
        Domain(id: work, slug: "zoe", name: "Work", label: "Your job and your craft",
               strokes: [GlyphStroke(d: "M 40,160 L 100,40 L 160,160 Z", width: 6)], viewBox: "0 0 200 200"),
        Domain(id: friends, slug: "amis", name: "Friends", label: "The people you choose",
               strokes: [GlyphStroke(d: "M 60,100 A 30,30 0 1 1 140,100", width: 6)], viewBox: "0 0 200 200"),
        Domain(id: growth, slug: "growth", name: "Personal growth", label: "Becoming", strokes: nil, viewBox: nil),
        Domain(id: spirit, slug: "spirit", name: "Spirituality", label: "What you believe", strokes: nil, viewBox: nil)
    ]

    static let collectionGlyphs: [UUID: Glyph] = [
        autumn: Glyph(id: UUID(), name: "Autumn", strokes: [GlyphStroke(d: "M 20,100 Q 100,20 180,100", width: 6)],
                      viewBox: "0 0 200 200", userId: nil)
    ]

    static let soulGlyphs: [String: [GlyphStroke]] = [
        "44444444-4444-4444-8444-444444444401": [GlyphStroke(d: "M 40,40 L 160,160", width: 6)]
    ]

    private static func decode(_ json: String) -> MomentDetail {
        do {
            return try JSONDecoder().decode(MomentDetail.self, from: Data(json.utf8))
        } catch {
            preconditionFailure("held card fixture does not decode: \(error)")
        }
    }

    // A fixture builder: its tuples are the JSON rows it writes, as they read in the fixtures below.
    // swiftlint:disable function_parameter_count large_tuple
    private static func detail(id: String, name: String?, words: String?, snap: String?, intensity: Int,
                               positiveness: Int, visibility: String, souls: [(String, String)],
                               domains: [(UUID, String, String)], collections: [(UUID, String)]) -> MomentDetail {
        // swiftlint:enable function_parameter_count large_tuple
        let snaps = snap.map {
            #"[{"id": "\#(UUID().uuidString.lowercased())", "storage_path": "\#($0)", "sort_order": 0}]"#
        } ?? "[]"
        let soulsJSON = souls.map { #"{"id": "\#($0.0)", "name": "\#($0.1)"}"# }.joined(separator: ",")
        let domainsJSON = domains
            .map { #"{"id": "\#($0.0.uuidString.lowercased())", "slug": "\#($0.1)", "name": "\#($0.2)"}"# }
            .joined(separator: ",")
        let collectionsJSON = collections
            .map { #"{"id": "\#($0.0.uuidString.lowercased())", "name": "\#($0.1)", "glyph_id": null}"# }
            .joined(separator: ",")
        return decode("""
        {
          "id": "\(id)", "user_id": "5f0e1d2c-3b4a-4958-8776-655443322110",
          "name": \(name.map { "\"\($0)\"" } ?? "null"), "description": \(words.map { "\"\($0)\"" } ?? "null"),
          "happened_at": "2026-10-08T19:00:00+00:00", "hidden_at": null,
          "created_at": "2026-10-08T19:10:00+00:00", "updated_at": "2026-10-08T19:10:00+00:00",
          "snaps": \(snaps),
          "pebble": {
            "id": "\(id)", "intensity": \(intensity), "positiveness": \(positiveness), "visibility": "\(visibility)",
            "emotion": {"id": "22222222-2222-4222-8222-222222222222", "slug": "joy", "name": "Joy"},
            "glyph": {"id": "66666666-6666-4666-8666-666666666666", "name": null,
                      "strokes": [{"d": "M 20,100 Q 100,20 180,100", "width": 6}], "view_box": "0 0 200 200",
                      "source": "collection"},
            "souls": [\(soulsJSON)], "domains": [\(domainsJSON)], "collections": [\(collectionsJSON)]
          }
        }
        """)
    }

    /// F1: a photo card, private, with three souls, two domains and one collection.
    static let f1 = detail(id: "0b7c3f5e-2d41-4a8e-9f6b-1c2d3e4f5a01", name: "Fountain at dusk",
                           words: "We stayed until the lights came on.", snap: fountain, intensity: 2, positiveness: 1,
                           visibility: "private",
                           souls: [("44444444-4444-4444-8444-444444444401", "Ana"),
                                   ("44444444-4444-4444-8444-444444444402", "Elio"),
                                   ("44444444-4444-4444-8444-444444444403", "Mara")],
                           domains: [(friends, "amis", "Friends"), (work, "zoe", "Work")],
                           collections: [(autumn, "Autumn")])

    /// F2: a public portrait card, so Share shows.
    static let f2 = detail(id: "0b7c3f5e-2d41-4a8e-9f6b-1c2d3e4f5a02", name: "Seawall", words: nil, snap: seawall,
                           intensity: 3, positiveness: 0, visibility: "public",
                           souls: [], domains: [(friends, "amis", "Friends")], collections: [])

    /// F3: a lowlight with no photo, secret: the slip carries the name.
    static let f3 = detail(id: "0b7c3f5e-2d41-4a8e-9f6b-1c2d3e4f5a03", name: "The long wait at the clinic",
                           words: "Nobody called my name for two hours.", snap: nil, intensity: 1, positiveness: -1,
                           visibility: "secret", souls: [], domains: [(work, "zoe", "Work")], collections: [])

    /// The stress verso: four long domains, two long collections, a highlight of the month.
    static let stress = detail(id: "0b7c3f5e-2d41-4a8e-9f6b-1c2d3e4f5a04", name: "Everything at once", words: nil,
                               snap: nil, intensity: 3, positiveness: 1, visibility: "private", souls: [],
                               domains: [(work, "zoe", "Work"), (friends, "amis", "Friends"),
                                         (growth, "growth", "Personal growth"), (spirit, "spirit", "Spirituality")],
                               collections: [(autumn, "Autumn evenings on the river"),
                                             (UUID(), "Things I want to remember next year")])
}

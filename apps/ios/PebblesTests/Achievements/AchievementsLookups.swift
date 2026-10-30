import Foundation
@testable import Pebbles

/// The canonical design fixture's ids given faces (#1306): the emotions and their
/// seven families are the real reference rows (`packages/supabase/reference/`,
/// every palette whole), the domains the fixture's own list beside it
/// (`design/domains.json`). Read through `#filePath`, as the fixture is.
@MainActor
enum AchievementsLookups {
    private struct CategoryRow: Decodable {
        let id: UUID
        let slug: String
        let name: String
        let primary: String
        let secondary: String
        let light: String
        let surface: String
        let shaded: String
        let dark: String

        enum CodingKeys: String, CodingKey {
            case id, slug, name
            case primary = "primary_color"
            case secondary = "secondary_color"
            case light = "light_color"
            case surface = "surface_color"
            case shaded = "shaded_color"
            case dark = "dark_color"
        }
    }

    private struct EmotionRow: Decodable {
        let id: String
        let slug: String
        let name: String
        let emoji: String
        let categorySlug: String

        enum CodingKeys: String, CodingKey {
            case id, slug, name, emoji
            case categorySlug = "category_slug"
        }
    }

    private struct DomainRow: Decodable {
        let id: UUID
        let slug: String
        let name: String
    }

    static let reference = AchievementsFixtures.oracles
        .deletingLastPathComponent() // oracles
        .deletingLastPathComponent() // scripts
        .appendingPathComponent("../reference")
        .standardizedFileURL

    /// The seven families, each with its real palette.
    static func families() throws -> [EmotionCategory] {
        let rows = try JSONDecoder().decode([CategoryRow].self,
                                            from: Data(contentsOf: reference.appendingPathComponent("emotion-categories.json")))
        return rows.compactMap { row in
            EmotionPalette(primaryHex: row.primary, secondaryHex: row.secondary, lightHex: row.light,
                           surfaceHex: row.surface, darkHex: row.dark, shadedHex: row.shaded)
                .map { EmotionCategory(id: row.id, slug: row.slug, name: row.name, palette: $0) }
        }
    }

    /// The fixture's lookups: every family, emotion and domain it names resolves.
    static func fixture() throws -> WrapLookups {
        let families = Dictionary(uniqueKeysWithValues: try Self.families().map { ($0.slug, $0) })
        let emotionRows = try JSONDecoder().decode([EmotionRow].self,
                                                   from: Data(contentsOf: reference.appendingPathComponent("emotions.json")))
        var emotions: [String: EmotionWithPalette] = [:]
        for row in emotionRows {
            guard let category = families[row.categorySlug], let emotion = Self.row(row, category: category) else { continue }
            emotions[row.id] = emotion
        }
        let domainRows = try JSONDecoder().decode([DomainRow].self, from: AchievementsFixtures.data("domains"))
        let domains = Dictionary(uniqueKeysWithValues: domainRows.map {
            ($0.id.uuidString.lowercased(), Domain(id: $0.id, slug: $0.slug, name: $0.name, label: $0.name))
        })
        return WrapLookups(family: { families[$0] }, domain: { domains[$0.lowercased()] },
                           emotion: { emotions[$0.lowercased()] })
    }

    private static func row(_ row: EmotionRow, category: EmotionCategory) -> EmotionWithPalette? {
        let palette = category.palette
        let json: [String: String] = [
            "id": row.id, "slug": row.slug, "name": row.name, "emoji": row.emoji,
            "category_id": category.id.uuidString, "category_slug": category.slug, "category_name": category.name,
            "primary_color": palette.primaryHex, "secondary_color": palette.secondaryHex,
            "light_color": palette.lightHex, "surface_color": palette.surfaceHex,
            "dark_color": palette.darkHex, "shaded_color": palette.shadedHex
        ]
        guard let data = try? JSONEncoder().encode(json) else { return nil }
        return try? JSONDecoder().decode(EmotionWithPalette.self, from: data)
    }
}

import Foundation
@testable import Pebbles

/// The design fixture's ids given faces (#1306): each seat's emotion takes its
/// family's fixture palette (`SpineFixtureWeek.families`, which has no shame
/// family, so shame draws in ink: the unknown-family fallback, on purpose) and
/// the board's name for that seat; the domains are named in the order they came.
@MainActor
enum AchievementsLookups {
    static let emotionNames = [
        "Amused", "Cheerful", "Content", "Excited", "Grateful", "Joyful",
        "Accomplished", "Confident", "Proud", "Respected", "Worthy",
        "Angry", "Annoyed", "Bitter", "Frustrated", "Irritated", "Resentful",
        "Anxious", "Insecure", "Nervous", "Overwhelmed", "Scared", "Worried",
        "Ashamed", "Embarrassed", "Guilty", "Humiliated",
        "Disappointed", "Hurt", "Lonely", "Melancholic", "Sad", "Tired",
        "Calm", "Grounded", "Peaceful", "Relaxed", "Relieved"
    ]

    static let domainNames = ["Family", "Friends", "Home", "Work", "Health", "Partner", "Travel", "Hobbies", "Learning",
                              "Identity", "Nature", "Money", "Body", "Events", "Spirituality", "Community",
                              "Creativity", "Rest"]

    static func fixture(_ page: AchievementsPage) -> WrapLookups {
        let families = Dictionary(uniqueKeysWithValues: SpineFixtureWeek.families.map { ($0.slug, $0) })
        var emotions: [String: EmotionWithPalette] = [:]
        for seat in page.emotions?.seats ?? [] {
            guard let family = seat.family, let id = seat.emotionId, let category = families[family],
                  emotionNames.indices.contains(seat.seat) else { continue }
            emotions[id] = row(id, name: emotionNames[seat.seat], category: category)
        }
        let ids = (page.domains?.arrival.compactMap(\.domainId) ?? []) + (page.domains?.notYet ?? [])
        var domains: [String: Domain] = [:]
        for (index, id) in ids.enumerated() {
            let name = domainNames[index % domainNames.count]
            // A slug no catalog entry has, so `localizedName` reads the fixture's name in every language.
            domains[id] = Domain(id: UUID(uuidString: id) ?? UUID(), slug: "fixture-\(name.lowercased())", name: name,
                                 label: name)
        }
        return WrapLookups(family: { families[$0] }, domain: { domains[$0] }, emotion: { emotions[$0] })
    }

    private static func row(_ id: String, name: String, category: EmotionCategory) -> EmotionWithPalette? {
        let palette = category.palette
        let json = """
        {"id":"\(id)","slug":"fixture-\(name.lowercased())","name":"\(name)","emoji":"🙂",
         "category_id":"\(category.id.uuidString)","category_slug":"\(category.slug)","category_name":"\(category.name)",
         "primary_color":"\(palette.primaryHex)","secondary_color":"\(palette.secondaryHex)",
         "light_color":"\(palette.lightHex)","surface_color":"\(palette.surfaceHex)",
         "dark_color":"\(palette.darkHex)","shaded_color":"\(palette.shadedHex)"}
        """
        return try? JSONDecoder().decode(EmotionWithPalette.self, from: Data(json.utf8))
    }
}

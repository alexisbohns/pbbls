import Foundation
import Testing
@testable import Pebbles

@Suite("Achievement family copy · the first five")
struct AchievementFamilyCopyTests {

    static func record(_ family: String, slug: String) -> AchievementRecord {
        AchievementRecord(
            id: UUID(), slug: slug, family: family, threshold: nil,
            emotionId: nil, domainId: nil, sortOrder: 0, glyphId: nil,
            karmaReward: 0, isActive: true,
            titleEn: nil, titleFr: nil, descriptionEn: nil, descriptionFr: nil
        )
    }

    @Test func theThreeNewFamiliesHaveTitlesDescriptionsAndSymbols() {
        let moment = Self.record("first_moment", slug: "first-moment")
        let pebble = Self.record("first_pebble", slug: "first-pebble")
        let five = Self.record("first_five", slug: "blue-pebble")
        #expect(moment.localizedTitle(emotionName: nil, domainName: nil) == "First moment")
        #expect(pebble.localizedTitle(emotionName: nil, domainName: nil) == "First pebble")
        #expect(five.localizedTitle(emotionName: nil, domainName: nil) == "The blue pebble")
        #expect(moment.localizedDescription(emotionName: nil, domainName: nil) == "Record your first moment.")
        #expect(pebble.localizedDescription(emotionName: nil, domainName: nil) == "Complete your first pebble.")
        #expect(five.localizedDescription(emotionName: nil, domainName: nil) == "Claim it once your first five are done.")
        #expect(moment.familySymbol == .pebbleFlat && pebble.familySymbol == .pebbleRound)
        #expect(five.familySymbol == .stackLit3)
        #expect(AchievementRecord.localizedGroupName(family: "first_five") == "The blue pebble")
        #expect(AchievementRecord.localizedGroupName(family: "first_moment") == "First moment")
        #expect(AchievementRecord.localizedGroupName(family: "first_pebble") == "First pebble")
    }

    /// Same per-language `.lproj` mechanism as `LocalizationTests`.
    @Test func everyNewKeyIsTranslatedInBothLanguages() {
        let expected: [String: (en: String, fr: String)] = [
            "achievement.family.first_moment.title": ("First moment", "Premier moment"),
            "achievement.family.first_moment.description": ("Record your first moment.", "Enregistre ton premier moment."),
            "achievement.family.first_pebble.title": ("First pebble", "Premier galet"),
            "achievement.family.first_pebble.description": ("Complete your first pebble.", "Complète ton premier galet."),
            "achievement.family.first_five.title": ("The blue pebble", "Le galet bleu"),
            "achievement.family.first_five.description": ("Claim it once your first five are done.", "Réclame-le quand tes cinq premières fois sont faites."),
            "achievement.group.first_moment": ("First moment", "Premier moment"),
            "achievement.group.first_pebble": ("First pebble", "Premier galet"),
            "achievement.group.first_five": ("The blue pebble", "Le galet bleu")
        ]
        for (key, values) in expected {
            #expect(Self.resolve(key, language: "en") == values.en, "\(key) en")
            #expect(Self.resolve(key, language: "fr") == values.fr, "\(key) fr")
        }
    }

    private static func resolve(_ key: String, language: String) -> String {
        guard let path = Bundle.main.path(forResource: language, ofType: "lproj"),
              let bundle = Bundle(path: path) else { return "__NO_BUNDLE__" }
        return bundle.localizedString(forKey: key, value: "__MISSING__", table: nil)
    }
}

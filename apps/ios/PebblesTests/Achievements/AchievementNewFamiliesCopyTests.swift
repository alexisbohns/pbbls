import Foundation
import Testing
@testable import Pebbles

/// The six firsts (`first_stone`) and the completions (`set_complete`) on the
/// shelf and the grid (#1305 D5, D9). Their titles are the catalog's seeded
/// `title_en`/`title_fr`; the client adds descriptions. The test process runs
/// in English, as `AchievementFamilyCopyTests` does.
@Suite("Achievement copy for the new families")
struct AchievementNewFamiliesCopyTests {
    static func record(_ slug: String, _ family: String, title: String?) -> AchievementRecord {
        AchievementRecord(
            id: UUID(), slug: slug, family: family, threshold: nil,
            emotionId: nil, domainId: nil, sortOrder: 460, glyphId: nil,
            karmaReward: 0, isActive: true,
            titleEn: title, titleFr: nil, descriptionEn: nil, descriptionFr: nil
        )
    }

    @Test("the six firsts carry the catalog's title and one shared description")
    func sixFirsts() {
        let large = Self.record("first-stone-large", "first_stone", title: "First large pebble")
        #expect(large.localizedTitle(emotionName: nil, domainName: nil) == "First large pebble")
        #expect(large.localizedDescription(emotionName: nil, domainName: nil) == "One of the six firsts.")
    }

    @Test("a completion carries the catalog's title and its ceremony's words")
    func completions() {
        let emotions = Self.record("complete-emotions", "set_complete", title: "Every emotion")
        #expect(emotions.localizedTitle(emotionName: nil, domainName: nil) == "Every emotion")
        #expect(emotions.localizedDescription(emotionName: nil, domainName: nil)
            == "Your pebbles have carried every feeling on the wheel.")
        let six = Self.record("complete-six-firsts", "set_complete", title: "The six firsts")
        #expect(six.localizedDescription(emotionName: nil, domainName: nil)
            == "Small, medium and large; lowlight, neutral and highlight.")
        #expect(Self.record("complete-souls", "set_complete", title: nil)
            .localizedDescription(emotionName: nil, domainName: nil) == nil)
    }

    @Test("the new description is translated in both languages")
    func translated() {
        for (language, value) in [("en", "One of the six firsts."), ("fr", "L'une des six premières fois.")] {
            let bundle = Bundle.main.path(forResource: language, ofType: "lproj").flatMap(Bundle.init(path:))
            #expect(bundle?.localizedString(forKey: "achievement.family.first_stone.description",
                                            value: "__MISSING__", table: nil) == value)
        }
    }
}

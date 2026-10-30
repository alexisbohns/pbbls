import Foundation
import Testing
@testable import Pebbles

/// The Profile shelf from the served row (#1306, D18): the server's six, in its
/// order, titled from the catalog by slug, or from the served row without it.
@Suite("Profile shelf from the served row")
struct ProfileShelfTests {
    static func record(_ slug: String, _ family: String, title: String) -> AchievementRecord {
        AchievementRecord(id: UUID(), slug: slug, family: family, threshold: nil, emotionId: nil, domainId: nil,
                          sortOrder: 0, glyphId: nil, karmaReward: 0, isActive: true,
                          titleEn: title, titleFr: nil, descriptionEn: nil, descriptionFr: nil)
    }

    @Test("the served six, in the server's order and count; the catalog titles them by slug")
    func served() throws {
        let page = try AchievementsFixtures.payload("design-fixture").model
        let catalog = ["first-glyph": Self.record("first-glyph", "first_glyph", title: "Carved")]
        let badges = ProfileShelf.badges(page, catalog: catalog)
        #expect(badges.map(\.slug)
            == ["glyph-sales-1", "glyph-count-10", "pebble-count-100", "pebble-count-50", "blue-pebble", "first-glyph"])
        #expect(badges.last?.localizedTitle(emotionName: nil, domainName: nil) == "Carved")
        #expect(badges[2].threshold == 100 && badges[2].family == "pebble_count", "no catalog row: the served row")
        #expect(ProfileShelf.count(page) == 13)
    }

    @Test("no shelf served, or none earned: no badges and no count, the plain invitation")
    func missing() throws {
        #expect(ProfileShelf.badges(nil, catalog: [:]).isEmpty && ProfileShelf.count(nil) == nil)
        let bare = try AchievementsFixtures.json(#"{"years": []}"#).model
        #expect(ProfileShelf.badges(bare, catalog: [:]).isEmpty && ProfileShelf.count(bare) == nil)
        let none = try AchievementsFixtures.json(#"{"shelf": [], "shelf_count": 0}"#).model
        #expect(ProfileShelf.count(none) == nil)
    }
}

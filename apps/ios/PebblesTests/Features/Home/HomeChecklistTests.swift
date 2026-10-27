import Foundation
import Testing
@testable import Pebbles

@Suite("Home checklist") struct HomeChecklistTests {
    static func catalog(_ slugs: [String], inactive: Set<String> = []) -> [AchievementRecord] {
        slugs.enumerated().map { index, slug in
            AchievementRecord(
                id: UUID(), slug: slug,
                family: slug == HomeChecklist.blueSlug ? "first_five" : slug.replacingOccurrences(of: "-", with: "_"),
                threshold: nil, emotionId: nil, domainId: nil, sortOrder: index, glyphId: nil,
                karmaReward: 0, isActive: !inactive.contains(slug),
                titleEn: nil, titleFr: nil, descriptionEn: nil, descriptionFr: nil
            )
        }
    }

    static var allSlugs: [String] { HomeChecklist.Task.allCases.map(\.slug) + [HomeChecklist.blueSlug] }

    @Test func rowsFollowTheFixedOrderAndTickFromUnlocks() {
        let c = HomeChecklist.make(catalog: Self.catalog(Self.allSlugs), unlocked: ["first-moment", "first-soul"])
        #expect(c.rows.map(\.task) == [.moment, .pebble, .collection, .soul, .glyph])
        #expect(c.rows.map(\.isDone) == [true, false, false, true, false])
        #expect(c.doneCount == 2 && !c.canClaim && c.state == .inProgress)
    }

    @Test func rowsCarryTheirUnlockDate() {
        let at = Date(timeIntervalSince1970: 1_793_000_000)
        let c = HomeChecklist.make(catalog: Self.catalog(Self.allSlugs), unlocked: ["first-soul"],
                                   unlockedAt: ["first-soul": at])
        #expect(c.rows[3].unlockedAt == at && c.rows[0].unlockedAt == nil)
    }

    @Test func fiveDoneOffersTheClaim() {
        let c = HomeChecklist.make(catalog: Self.catalog(Self.allSlugs),
                                   unlocked: Set(HomeChecklist.Task.allCases.map(\.slug)))
        #expect(c.canClaim && c.state == .claimable && c.doneCount == 5)
    }

    @Test func aClaimedBluePebbleRemovesTheSection() {
        let c = HomeChecklist.make(catalog: Self.catalog(Self.allSlugs), unlocked: Set(Self.allSlugs))
        #expect(c.state == .done)
    }

    @Test func aMissingOrRetiredCatalogRowHidesTheSection() {
        #expect(HomeChecklist.make(catalog: [], unlocked: []).state == .unavailable)
        let retired = Self.catalog(Self.allSlugs, inactive: ["first-glyph"])
        #expect(HomeChecklist.make(catalog: retired, unlocked: []).state == .unavailable)
        let noBlue = Self.catalog(HomeChecklist.Task.allCases.map(\.slug))
        #expect(HomeChecklist.make(catalog: noBlue, unlocked: []).state == .unavailable)
    }

    @Test func anUnlockedRetiredRowStillCounts() {
        // A retired row the user already unlocked keeps its tick (unlocks are permanent).
        let retired = Self.catalog(Self.allSlugs, inactive: ["first-glyph"])
        let c = HomeChecklist.make(catalog: retired, unlocked: ["first-glyph"])
        #expect(c.state != .unavailable && c.rows[4].isDone)
    }
}

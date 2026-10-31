import Foundation
import Testing
@testable import Pebbles

/// `YouServicing` for `YouModel`'s tests: fixed answers, the calls counted.
final class FakeYouService: YouServicing, @unchecked Sendable {
    struct Failure: Error {}
    private let lock = NSLock()
    private let profileAnswer: Result<YouProfile, Error>
    private let strokesAnswer: Result<[GlyphStroke], Error>
    private var profileCallCount = 0

    init(profile: Result<YouProfile, Error>, strokes: Result<[GlyphStroke], Error> = .success([])) {
        profileAnswer = profile
        strokesAnswer = strokes
    }

    var profileCalls: Int { lock.withLock { profileCallCount } }

    func profile() async throws -> YouProfile {
        try lock.withLock {
            profileCallCount += 1
            return try profileAnswer.get()
        }
    }

    func glyphStrokes(id: UUID) async throws -> [GlyphStroke] { try strokesAnswer.get() }
}

@Suite("You model") @MainActor
struct YouModelTests {
    private static let strokes = [GlyphStroke(d: "M40,40 L160,160", width: 6)]

    @Test func loadsTheNameTheDateAndTheGlyph() async {
        let model = YouModel()
        model.service = FakeYouService(profile: .success(YouFixture.profile), strokes: .success(Self.strokes))
        await model.loadProfile()
        #expect(model.profile?.displayName == "Alexis")
        #expect(model.profile?.createdAt == YouFixture.profile.createdAt)
        #expect(model.glyphStrokes == Self.strokes)
    }

    @Test func aFailedReadLeavesThemEmpty() async {
        let model = YouModel()
        model.service = FakeYouService(profile: .failure(FakeYouService.Failure()))
        await model.loadProfile()
        #expect(model.profile == nil && model.glyphStrokes == nil)
        #expect(model.hasLoadedProfile)
    }

    @Test func aSecondLoadIsANoOp() async {
        let model = YouModel()
        let fake = FakeYouService(profile: .success(YouFixture.profile))
        model.service = fake
        await model.loadProfile()
        await model.loadProfile()
        #expect(fake.profileCalls == 1)
    }

    @Test func settingsApplyWithoutARead() async {
        let model = YouModel()
        model.service = FakeYouService(profile: .success(YouFixture.profile))
        await model.loadProfile()
        model.applySettings(name: "Alex", glyph: (UUID(), Self.strokes), handle: nil, isPublic: false)
        #expect(model.profile?.displayName == "Alex" && model.glyphStrokes == Self.strokes)
        #expect(model.profile?.publicProfile == false)
    }

    // MARK: - Glyphs lines (#1382 D19)

    @Test func glyphLinesCountInviteOrStayPlain() {
        let en = Locale(identifier: "en")
        #expect(YouGlyphsSection.line("you.glyphs.mine", 12, locale: en) == "The 12 marks you carved.")
        #expect(YouGlyphsSection.line("you.glyphs.mine", 1, locale: en) == "The mark you carved.")
        #expect(YouGlyphsSection.line("you.glyphs.mine", 0, locale: en) == "Carve your first mark.")
        #expect(YouGlyphsSection.line("you.glyphs.mine", nil, locale: en) == "The marks you carved.")
        let fr = Locale(identifier: "fr")
        #expect(YouGlyphsSection.line("you.glyphs.owned", 5, locale: fr) == "Les 5 que tu as reçus des autres.")
        #expect(YouGlyphsSection.line("you.glyphs.owned", 1, locale: fr) == "Celui que tu as reçu des autres.")
    }

    // MARK: - Achievements (#1382 D20)

    private static func shelfPage() throws -> AchievementsPage {
        try WrapDecoding.decode(AchievementsPayload.self, from: Data(YouFixture.shelfJSON.utf8)).model
    }

    @Test func theShelfReadsOnlyWhenStaleAndGrantsFirst() async throws {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let page = try Self.shelfPage()
        let recent = FakeAchievementsPageService(kept: page, answers: [.success(page)])
        recent.keptAtDate = now.addingTimeInterval(-5 * 60)
        let provider = FakeAchievementsProvider()
        let model = YouModel()
        model.shelf.service = recent
        model.achievements = provider
        await model.loadShelf(now: { now })
        #expect(recent.calls == ["kept"] && provider.checkCalls == 0, "a fresh kept copy: no read, no grant")

        let old = FakeAchievementsPageService(kept: page, answers: [.success(page)])
        old.keptAtDate = now.addingTimeInterval(-20 * 60)
        let stale = YouModel()
        stale.shelf.service = old
        stale.achievements = provider
        await stale.loadShelf(now: { now })
        #expect(old.calls == ["kept", "read"] && provider.checkCalls == 1)
        #expect(stale.earnedCount == 12 && stale.badges.count == 4)
    }

    @Test func aFailedCatalogKeepsTheServedRows() async throws {
        let provider = FakeAchievementsProvider()
        provider.catalogError = FakeAchievementsProvider.Failure()
        let model = YouModel()
        model.shelf.service = FakeAchievementsPageService(answers: [.success(try Self.shelfPage())])
        model.achievements = provider
        await model.loadShelf()
        #expect(model.badges.map(\.slug) == ["first-pebble", "first-emotion", "first-soul", "first-collection"])
        #expect(provider.catalogCalls == 1 && model.catalog.isEmpty)
    }

    @Test func theSectionShowsAtMostFourBadges() {
        let six = (0..<6).map { index in
            AchievementRecord(id: UUID(), slug: "s\(index)", family: "first_pebble", threshold: nil, emotionId: nil,
                              domainId: nil, sortOrder: index, glyphId: nil, karmaReward: 0, isActive: true,
                              titleEn: nil, titleFr: nil, descriptionEn: nil, descriptionFr: nil)
        }
        #expect(YouAchievementsSection.shown(six).count == 4)
        #expect(YouAchievementsSection.shown(Array(six.prefix(2))).count == 2)
    }

    @Test func theEarnedWordIsPlural() {
        #expect(YouAchievementsSection.earnedWord(12, locale: Locale(identifier: "en")) == "earned")
        #expect(YouAchievementsSection.earnedWord(1, locale: Locale(identifier: "fr")) == "décroché")
        #expect(YouAchievementsSection.earnedWord(12, locale: Locale(identifier: "fr")) == "décrochés")
    }

    // MARK: - The Lab (#1382 D21)

    @Test func theLabReadsBothFeeds() async {
        let model = YouModel()
        let lab = FakeYouLab(announcements: .success([YouFixture.announcement]),
                             changelog: .success(YouFixture.changelog))
        model.lab = lab
        await model.loadLab()
        #expect(model.announcement == YouFixture.announcement && model.changelog.count == 3)
        #expect(lab.limits == ["announcements:1", "changelog:3"] || lab.limits == ["changelog:3", "announcements:1"])
    }

    @Test func oneFailingFeedLeavesTheOther() async {
        let model = YouModel()
        model.lab = FakeYouLab(announcements: .failure(FakeYouLab.Failure()),
                               changelog: .success(YouFixture.changelog))
        await model.loadLab()
        #expect(model.announcement == nil && model.changelog.count == 3)
        let other = YouModel()
        other.lab = FakeYouLab(announcements: .success([YouFixture.announcement]),
                               changelog: .failure(FakeYouLab.Failure()))
        await other.loadLab()
        #expect(other.announcement != nil && other.changelog.isEmpty)
    }

    @Test func bothFailingLeaveOnlyTheCommunity() async {
        let model = YouModel()
        model.lab = FakeYouLab(announcements: .failure(FakeYouLab.Failure()),
                               changelog: .failure(FakeYouLab.Failure()))
        await model.loadLab()
        #expect(model.announcement == nil && model.changelog.isEmpty)
    }
}

/// `YouLabServicing` for the Lab tests: fixed answers, the limits asked recorded.
@MainActor
final class FakeYouLab: YouLabServicing {
    struct Failure: Error {}
    let announcementsAnswer: Result<[Log], Error>
    let changelogAnswer: Result<[Log], Error>
    private(set) var limits: [String] = []

    init(announcements: Result<[Log], Error>, changelog: Result<[Log], Error>) {
        announcementsAnswer = announcements
        changelogAnswer = changelog
    }

    func announcements(limit: Int?) async throws -> [Log] {
        limits.append("announcements:\(limit ?? 0)")
        return try announcementsAnswer.get()
    }

    func changelog(limit: Int?) async throws -> [Log] {
        limits.append("changelog:\(limit ?? 0)")
        return try changelogAnswer.get()
    }
}

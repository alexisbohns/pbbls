#if DEBUG
import Foundation

/// The lab's and the references' Home (#1255): the harness's verbatim
/// `home-wraps.json` (embedded here; a test keeps it equal to the harness's
/// file), a catalog for the first five and the blue pebble, three guides, and
/// the fixture October's lookups giving the cover's ids faces. Three states:
/// a new user, a returning one, and one with the five done and the claim open.
@MainActor
enum HomeFixture {
    enum State: String, CaseIterable, Identifiable {
        case newUser = "new user", returning, claim
        var id: String { rawValue }
    }

    /// A Saturday evening in Paris, the greeting's clock (the c3 mock's, #1382).
    nonisolated static let clock: Date = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f.date(from: "2026-10-31T19:30:00+01:00") ?? .distantPast
    }()
    static let zone = TimeZone(identifier: "Europe/Paris")!
    static let liveMonth = CalendarMonth(year: 2026, month: 10)

    /// The live month the week columns draw: the harness's October.
    static var month: MonthWrap? { SpineFixtureMonth.october }

    /// The harness's months as `home_wraps` returned them (minified).
    static var wraps: HomeWraps? {
        try? WrapDecoding.decode(HomeWrapsPayload.self, from: Data(wrapsJSON.utf8)).model
    }

    /// The fixture October's faces for the cover's ids; blank when it fails to decode.
    static let lookups: WrapLookups =
        SpineFixtureMonth.october.map { SpineFixtureMonth.lookups($0) } ?? WrapLookups()

    private static let slugs = HomeChecklist.Task.allCases.map(\.slug) + [HomeChecklist.blueSlug]

    /// The first five and the blue pebble, with stable ids.
    static let catalog: [AchievementRecord] = slugs.enumerated().map { index, slug in
        AchievementRecord(
            id: UUID(uuidString: String(format: "00000000-0000-0000-0000-0000000000%02d", index + 1)) ?? UUID(),
            slug: slug,
            family: slug == HomeChecklist.blueSlug ? "first_five" : slug.replacingOccurrences(of: "-", with: "_"),
            threshold: nil, emotionId: nil, domainId: nil, sortOrder: index, glyphId: nil,
            karmaReward: 0, isActive: true,
            titleEn: nil, titleFr: nil, descriptionEn: nil, descriptionFr: nil
        )
    }

    /// Unlocks for `slugs`, each a day before the clock.
    static func unlocks(_ slugs: Set<String>) -> [AchievementUnlockRecord] {
        catalog.filter { slugs.contains($0.slug) }.map {
            AchievementUnlockRecord(achievementId: $0.id, unlockedAt: clock.addingTimeInterval(-86_400))
        }
    }

    static let guides: [Guide] = [
        Guide(id: UUID(uuidString: "00000000-0000-0000-0001-000000000001")!, slug: "stones", sortOrder: 1,
              illustration: .stones,
              titleEn: "Stones", titleFr: "Les pierres",
              summaryEn: "Nine shapes, by weight and light.", summaryFr: "Neuf formes, par poids et lumière.",
              bodyMdEn: "A pebble is a stone you set down for a moment that mattered.\n\n"
                  + "Its shape follows the moment's weight, its light follows how it felt.",
              bodyMdFr: "Un pebble est une pierre posée pour un moment qui a compté.\n\n"
                  + "Sa forme suit le poids du moment, sa lumière suit ce qu'il a fait ressentir."),
        Guide(id: UUID(uuidString: "00000000-0000-0000-0001-000000000002")!, slug: "emotions", sortOrder: 2,
              illustration: .emotions,
              titleEn: "Emotions", titleFr: "Les émotions",
              summaryEn: "Seven families, each with a colour.", summaryFr: "Sept familles, chacune sa couleur.",
              bodyMdEn: "Every pebble carries one emotion, from seven families.\n\n"
                  + "The family gives it its colour; the fine emotion gives it its name.",
              bodyMdFr: "Chaque pebble porte une émotion, parmi sept familles.\n\n"
                  + "La famille lui donne sa couleur ; l'émotion fine lui donne son nom."),
        Guide(id: UUID(uuidString: "00000000-0000-0000-0001-000000000003")!, slug: "souls", sortOrder: 3,
              illustration: .souls,
              titleEn: "Souls", titleFr: "Les âmes",
              summaryEn: "The people in your moments.", summaryFr: "Les personnes de tes moments.",
              bodyMdEn: "A soul is someone who was there.\n\n"
                  + "Name them once, and every moment you share lands on their page too.",
              bodyMdFr: "Une âme est quelqu'un qui était là.\n\n"
                  + "Nomme-la une fois, et chaque moment partagé se pose aussi sur sa page.")
    ]

    /// A loaded model for `state`, on the fixture's clock and zone.
    static func model(_ state: State) async -> HomeModel {
        let model = HomeModel()
        model.zone = zone
        let months: HomeWraps
        let unlocked: Set<String>
        switch state {
        case .newUser:
            months = HomeWraps(months: [])
            unlocked = []
        case .returning:
            months = wraps ?? HomeWraps(months: [])
            unlocked = Set(slugs)
        case .claim:
            months = wraps ?? HomeWraps(months: [])
            unlocked = Set(HomeChecklist.Task.allCases.map(\.slug))
        }
        model.service = HomeFixtureService(name: "Alexis", wraps: months, guides: guides, month: month)
        model.achievements = HomeFixtureAchievements(catalog: catalog, unlocks: unlocks(unlocked))
        await model.load()
        await model.loadMonth(liveMonth)
        return model
    }

    // swiftlint:disable line_length
    static let wrapsJSON = ##"""
{"months":[{"count":4,"cover":{"days":[{"day":2,"pebbles":[{"polarity":1,"intensity":2,"moment_id":"4aeb7c24-6ce3-40e5-9b98-80d7cd9d394d","emotion_id":"50a68890-f6bb-e67a-6655-e84ff0b71f49"}]},{"day":11,"pebbles":[{"polarity":0,"intensity":2,"moment_id":"8368be77-7342-4ce8-bec8-cae66df5144a","emotion_id":"50a68890-f6bb-e67a-6655-e84ff0b71f49"}]},{"day":19,"pebbles":[{"polarity":-1,"intensity":2,"moment_id":"f2510e6c-e8af-44ff-a7f5-60d69ac956e0","emotion_id":"50a68890-f6bb-e67a-6655-e84ff0b71f49"}]},{"day":27,"pebbles":[{"polarity":1,"intensity":2,"moment_id":"9fa3f1e3-89d7-427c-a557-ab556f8a7bee","emotion_id":"50a68890-f6bb-e67a-6655-e84ff0b71f49"}]}],"inks":["joy"],"papers":["bb427d8c-3597-4e94-b18b-acc3fea8d723","01e6e592-fed6-c8f3-c3ee-42e39d89d3a7"],"souls_in_order":["f1c08336-606c-4513-8f1b-12dd78fa7c58"]},"month":"2026-09-01","revealed_at":null}]}
"""##
    // swiftlint:enable line_length
}

/// Fixed answers for the Lab and the references; nothing reaches the server.
struct HomeFixtureService: HomeServicing {
    let name: String
    let wraps: HomeWraps
    let guides: [Guide]
    var month: MonthWrap?

    struct NoMonth: Error {}

    func displayName() async throws -> String? { name }
    func month(_ month: CalendarMonth, zone: TimeZone) async throws -> MonthWrap {
        guard let wrap = self.month else { throw NoMonth() }
        return wrap
    }
    func cachedMonth(_ month: CalendarMonth, zone: TimeZone) async -> MonthWrap? { self.month }
    func wraps(zone: TimeZone) async throws -> HomeWraps { wraps }
    func reveal(month: CalendarMonth, zone: TimeZone) async throws -> HomeReveal {
        HomeReveal(month: month, revealedAt: HomeFixture.clock)
    }
    func guides() async throws -> [Guide] { guides }
}

/// A fixed catalog and its unlocks; the claim grants nothing.
@MainActor
final class HomeFixtureAchievements: AchievementsProviding {
    let catalog: [AchievementRecord]
    let unlocks: [AchievementUnlockRecord]

    init(catalog: [AchievementRecord], unlocks: [AchievementUnlockRecord]) {
        self.catalog = catalog
        self.unlocks = unlocks
    }

    func checkIgnoringFailure() async {}
    func loadCatalog() async throws -> [AchievementRecord] { catalog }
    func loadUnlocks() async throws -> [AchievementUnlockRecord] { unlocks }
    func claimBluePebble() async throws -> [AchievementCheckResult] { [] }
}
#endif

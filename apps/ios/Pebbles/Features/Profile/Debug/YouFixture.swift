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

    static let stats = YouStats(rippleLevel: 3, activeToday: true, daysPracticed: 112, pebbles: 318, karma: 1240,
                                glyphsCarved: 12, glyphsOwned: 5)

    /// The served shelf (four badges, twelve earned) and the catalog that titles them.
    static let shelfJSON = #"""
    {"shelf": [
      {"slug": "first-pebble", "family": "first_pebble", "threshold": null, "unlocked_on": "2026-03-14"},
      {"slug": "first-emotion", "family": "emotion_first", "threshold": null, "unlocked_on": "2026-03-15"},
      {"slug": "first-soul", "family": "first_soul", "threshold": null, "unlocked_on": "2026-03-20"},
      {"slug": "first-collection", "family": "first_collection", "threshold": null, "unlocked_on": "2026-04-02"}
    ], "shelf_count": 12}
    """#

    static let catalog: [AchievementRecord] = [
        ("first-pebble", "first_pebble", "First pebble", "Premier galet"),
        ("first-emotion", "emotion_first", "Emotions", "Émotions"),
        ("first-soul", "first_soul", "Souls", "Âmes"),
        ("first-collection", "first_collection", "Collections", "Collections")
    ].enumerated().map { index, row in
        AchievementRecord(id: UUID(uuidString: String(format: "00000000-0000-0000-0003-0000000000%02d", index + 1))
                          ?? UUID(),
                          slug: row.0, family: row.1, threshold: nil, emotionId: nil, domainId: nil,
                          sortOrder: index, glyphId: nil, karmaReward: 0, isActive: true,
                          titleEn: row.2, titleFr: row.3, descriptionEn: nil, descriptionFr: nil)
    }

    private static func day(_ iso: String) -> Date {
        ISO8601DateFormatter().date(from: iso) ?? .distantPast
    }

    private static func log(_ id: Int, _ species: LogSpecies, _ title: (String, String),
                            _ summary: (String, String), on date: String) -> Log {
        Log(id: UUID(uuidString: String(format: "00000000-0000-0000-0004-0000000000%02d", id)) ?? UUID(),
            species: species, platform: .ios, status: .shipped,
            titleEn: title.0, titleFr: title.1, summaryEn: summary.0, summaryFr: summary.1,
            bodyMdEn: nil, bodyMdFr: nil, coverImagePath: nil, externalUrl: nil, published: true,
            publishedAt: day(date), releasedAt: day(date), createdAt: day(date), reactionCount: 0)
    }

    static let announcement = log(1, .announcement, ("Cut from one stone", "Taillé dans une seule pierre"),
                                  ("How the three quick actions on Home became one rock, split.",
                                   "Comment les trois actions rapides de l'accueil sont devenues "
                                       + "une seule pierre, taillée."),
                                  on: "2026-10-31T12:00:00Z")

    static let changelog = [
        log(2, .feature, ("Quick actions, cut from one stone", "Actions rapides, taillées dans une pierre"),
            ("Home", "Accueil"), on: "2026-10-31T12:00:00Z"),
        log(3, .feature, ("Hand-drawn icons, everywhere", "Des icônes dessinées à la main, partout"),
            ("iOS · Android", "iOS · Android"), on: "2026-10-30T12:00:00Z"),
        log(4, .feature, ("The year wheel", "La roue de l'année"),
            ("Achievements", "Succès"), on: "2026-10-28T12:00:00Z")
    ]

    /// A loaded model; `glyph: false` draws the header's empty mark, `shelf: false`
    /// the invitation, `announcement: false` the Lab without its card.
    static func model(glyph: Bool = true, shelf: Bool = true, announcement: Bool = true) async -> YouModel {
        let model = YouModel()
        var row = profile
        if !glyph { row.glyphId = nil }
        model.service = YouFixtureService(profile: row, strokes: strokes)
        model.achievements = HomeFixtureAchievements(catalog: catalog, unlocks: [])
        model.shelf.service = YouFixtureShelfService(json: shelf ? shelfJSON : #"{"shelf": [], "shelf_count": 0}"#)
        model.lab = YouFixtureLab(announcements: announcement ? [Self.announcement] : [], changelog: changelog)
        await model.loadProfile()
        await model.loadShelf()
        await model.loadLab()
        return model
    }
}

/// The served shelf from inline JSON; nothing is kept, pulled or marked.
struct YouFixtureShelfService: AchievementsPageServicing {
    let json: String
    struct Unavailable: Error {}

    func page(zone: TimeZone) async throws -> AchievementsPage {
        try WrapDecoding.decode(AchievementsPayload.self, from: Data(json.utf8)).model
    }
    func cachedPage(zone: TimeZone) async -> AchievementsPage? { nil }
    func keptAt(zone: TimeZone) async -> Date? { nil }
    func markStale() {}
    func markSeen(_ card: AchievementCard) async throws {}
    func pull(_ stack: AchievementsPage.CeremonyStack, zone: TimeZone) async throws -> CeremonyPull {
        throw Unavailable()
    }
}

/// Fixed Lab feeds.
@MainActor
struct YouFixtureLab: YouLabServicing {
    let announcements: [Log]
    let changelog: [Log]

    func announcements(limit: Int?) async throws -> [Log] { Array(announcements.prefix(limit ?? .max)) }
    func changelog(limit: Int?) async throws -> [Log] { Array(changelog.prefix(limit ?? .max)) }
}

/// Fixed answers; nothing reaches the server.
struct YouFixtureService: YouServicing {
    let profile: YouProfile
    let strokes: [GlyphStroke]

    func profile() async throws -> YouProfile { profile }
    func glyphStrokes(id: UUID) async throws -> [GlyphStroke] { strokes }
}
#endif

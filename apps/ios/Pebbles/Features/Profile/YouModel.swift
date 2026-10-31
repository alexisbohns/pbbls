import Foundation
import os
import Supabase

/// The caller's own `profiles` row, as You and Settings read it.
struct YouProfile: Decodable, Equatable {
    var displayName: String?
    let createdAt: Date
    var glyphId: UUID?
    var handle: String?
    var publicProfile: Bool

    enum CodingKeys: String, CodingKey {
        case displayName   = "display_name"
        case createdAt     = "created_at"
        case glyphId       = "glyph_id"
        case handle        = "handle"
        case publicProfile = "public_profile"
    }
}

/// You's reads, behind a protocol so the fixture and the tests use fakes
/// (the `HomeServicing` pattern, #1382 D16).
protocol YouServicing: Sendable {
    /// The caller's own row (RLS returns only it).
    func profile() async throws -> YouProfile
    /// One glyph's strokes, for the header's pentagon.
    func glyphStrokes(id: UUID) async throws -> [GlyphStroke]
}

struct YouService: YouServicing {
    let client: SupabaseClient

    func profile() async throws -> YouProfile {
        try await client
            .from("profiles")
            .select("display_name, created_at, glyph_id, handle, public_profile")
            .single().execute().value
    }

    func glyphStrokes(id: UUID) async throws -> [GlyphStroke] {
        // The `strokes` column is a JSONB array decoding into [GlyphStroke] (GlyphService.list()).
        struct GlyphRow: Decodable { let strokes: [GlyphStroke] }
        let row: GlyphRow = try await client
            .from("glyphs")
            .select("strokes")
            .eq("id", value: id.uuidString)
            .single().execute().value
        return row.strokes
    }
}

/// The figures You shows, as one snapshot: `PathStatsService` feeds it in the
/// app, the fixture in the references. Nil is "not loaded yet".
struct YouStats: Equatable {
    var rippleLevel: Int?
    var activeToday = false
    var daysPracticed: Int?
    var pebbles: Int?
    var karma: Int?
    var glyphsCarved: Int?
    var glyphsOwned: Int?
}

extension YouStats {
    @MainActor init(_ stats: PathStatsService) {
        self.init(rippleLevel: stats.ripple?.rippleLevel, activeToday: stats.ripple?.activeToday ?? false,
                  daysPracticed: stats.daysPracticed, pebbles: stats.pebbles, karma: stats.karma,
                  glyphsCarved: stats.glyphsCarved, glyphsOwned: stats.glyphsOwned)
    }
}

/// You's state (#1382 D16): the profile row and its glyph, moved out of
/// `ProfileView` so a fixture can draw the page.
@Observable @MainActor final class YouModel {
    private(set) var profile: YouProfile?
    private(set) var glyphStrokes: [GlyphStroke]?
    private(set) var hasLoadedProfile = false
    @ObservationIgnored var service: (any YouServicing)?
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "you.model")

    /// Once per model: a failed read logs and leaves the name and glyph empty.
    func loadProfile() async {
        guard !hasLoadedProfile, let service else { return }
        defer { hasLoadedProfile = true }
        do {
            let row = try await service.profile()
            profile = row
            if let glyphId = row.glyphId { await loadGlyph(glyphId) }
        } catch {
            logger.error("profile fetch failed: \(error.localizedDescription, privacy: .private)")
        }
    }

    private func loadGlyph(_ id: UUID) async {
        guard let service else { return }
        do {
            glyphStrokes = try await service.glyphStrokes(id: id)
        } catch {
            logger.error("glyph fetch failed: \(error.localizedDescription, privacy: .private)")
        }
    }

    // MARK: - Achievements (#1382 D20)

    /// The shelf: the achievements page's kept copy, read only when fifteen minutes old.
    let shelf = AchievementsModel()
    /// The catalog by slug, for the badges' titles and icons; empty until it lands.
    private(set) var catalog: [String: AchievementRecord] = [:]
    @ObservationIgnored var achievements: (any AchievementsProviding)?

    /// How old the kept copy may be before a visit to You reads again.
    static let shelfMaxAge: TimeInterval = 15 * 60

    /// The served badges, titled from the catalog (or from the served rows without it).
    var badges: [AchievementRecord] { ProfileShelf.badges(shelf.page, catalog: catalog) }
    /// The served count; nil when none is earned (the section then invites).
    var earnedCount: Int? { ProfileShelf.count(shelf.page) }

    /// Moved from `ProfileAchievementsCard` as it was: the kept copy, a read only
    /// when stale (the retroactive grant first, which never celebrates), then
    /// the catalog once. A failed catalog read keeps the served rows.
    func loadShelf(now: () -> Date = Date.init) async {
        let achievements = self.achievements
        await shelf.loadIfStale(maxAge: Self.shelfMaxAge, now: now) { await achievements?.checkIgnoringFailure() }
        guard catalog.isEmpty, !ProfileShelf.badges(shelf.page, catalog: [:]).isEmpty, let achievements else {
            return
        }
        do {
            let rows = try await achievements.loadCatalog()
            catalog = Dictionary(rows.map { ($0.slug, $0) }, uniquingKeysWith: { first, _ in first })
        } catch {
            logger.error("achievements catalog fetch failed: \(error.localizedDescription, privacy: .private)")
        }
    }

    // MARK: - The Lab (#1382 D21)

    /// The latest announcement and the three latest shipped features; each read fails alone.
    private(set) var announcement: Log?
    private(set) var changelog: [Log] = []
    @ObservationIgnored var lab: (any YouLabServicing)?

    func loadLab() async {
        guard let lab else { return }
        async let latest = Self.attempt("announcements", logger) { try await lab.announcements(limit: 1) }
        async let shipped = Self.attempt("changelog", logger) { try await lab.changelog(limit: 3) }
        if let logs = await latest { announcement = logs.first }
        if let logs = await shipped { changelog = Array(logs.prefix(3)) }
    }

    /// The read's rows, or nil (logged): a failed read keeps what is shown.
    private static func attempt(_ feed: String, _ logger: Logger,
                                _ read: () async throws -> [Log]) async -> [Log]? {
        do {
            return try await read()
        } catch {
            logger.error("lab \(feed, privacy: .public) failed: \(error.localizedDescription, privacy: .private)")
            return nil
        }
    }

    /// What Settings saved, applied without a read.
    func applySettings(name: String?, glyph: (id: UUID, strokes: [GlyphStroke])?, handle: String?,
                       isPublic: Bool) {
        if var current = profile {
            current.displayName = name
            current.glyphId = glyph?.id ?? current.glyphId
            current.handle = handle
            current.publicProfile = isPublic
            profile = current
        }
        if let strokes = glyph?.strokes { glyphStrokes = strokes }
    }
}
